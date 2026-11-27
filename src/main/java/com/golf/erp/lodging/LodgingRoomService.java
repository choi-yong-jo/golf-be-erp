package com.golf.erp.lodging;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.lodging.LodgingDtos.AvailableRoomResponse;
import com.golf.erp.lodging.LodgingDtos.RoomCreateRequest;
import com.golf.erp.lodging.LodgingDtos.RoomDetailResponse;
import com.golf.erp.lodging.LodgingDtos.RoomResponse;
import com.golf.erp.lodging.LodgingDtos.RoomUpdateRequest;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 객실(lodging_room): 목록·상세·등록·수정·상태 변경·청소 완료, 가용 조회.
 * <p>
 * <b>객실 타입 변경</b>: 같은 골프장의 타입으로만, 그리고 이 객실에 끝나지 않은 예약(입실 중이거나 입실 전이고 퇴실 예정
 * 시각이 지나지 않은 것)이 없을 때만 허용한다. 예약은 등록 시의 요금·대실 시간을 저장하므로 지난 예약에는 영향이 없다.
 * <p>
 * <b>상태</b>: MAINTENANCE·CLOSED 로는 입실 중인 예약이 있으면 바꿀 수 없다(409). 입실 전 예약 수는 응답으로 알려 준다.
 * 퇴실 처리하면 객실은 자동으로 CLEANING 이 되고, 청소 완료(cleaned)로 AVAILABLE 이 된다.
 * <p>
 * <b>가용</b>: 타입이 활성이고 객실이 AVAILABLE·CLEANING(청소는 입실 전까지 끝나는 것으로 본다)이며, 정원 ≥ guestCount,
 * 요청 시간대와 겹치는 점유 예약(PENDING·CONFIRMED·CHECKED_IN)이 없는 객실.
 */
@Service
@Transactional
public class LodgingRoomService {
	private final LodgingRoomRepository repo;
	private final LodgingRoomTypeRepository types;
	private final LodgingBookingRepository bookings;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public LodgingRoomService(LodgingRoomRepository repo, LodgingRoomTypeRepository types,
			LodgingBookingRepository bookings, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.types = types;
		this.bookings = bookings;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 객실 번호·이름. 타입 코드·객실 번호 순 */
	@Transactional(readOnly = true)
	public PageResult<RoomResponse> list(Long golfCourseId, Long roomTypeId, LodgingRoomStatus status, String keyword,
			int page, int size) {
		Specification<LodgingRoom> spec = Specification.allOf(Specs.eq("roomType.golfCourseId", golfCourseId),
				Specs.eq("roomType.id", roomTypeId), Specs.eq("status", status),
				Specs.like(keyword, "roomCode", "roomName"));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by("roomType.code", "roomCode", "id"))),
				RoomResponse::of);
	}

	@Transactional(readOnly = true)
	public RoomDetailResponse get(long id) {
		return detail(find(id), now());
	}

	public RoomDetailResponse create(RoomCreateRequest r) {
		LodgingRoomType t = findType(r.roomTypeId());
		String code = r.roomCode().trim();
		if (repo.existsByRoomType_IdAndRoomCode(t.getId(), code))
			throw new ConflictException("이 객실 타입에 이미 있는 객실 번호입니다: " + code);
		LocalDateTime now = now();
		LodgingRoom room = new LodgingRoom(t, code, blankToNull(r.roomName()), CurrentUser.idOrNull(), now);
		return detail(repo.save(room), now);
	}

	public RoomDetailResponse update(long id, RoomUpdateRequest r) {
		LodgingRoom room = lockRoom(id);
		LodgingRoomType current = room.getRoomType();
		LodgingRoomType target = current;
		LocalDateTime now = now();
		if (!r.roomTypeId().equals(current.getId())) {
			target = findType(r.roomTypeId());
			if (!target.getGolfCourseId().equals(current.getGolfCourseId()))
				throw new IllegalArgumentException("다른 골프장의 객실 타입으로 바꿀 수 없습니다.");
			long unfinished = bookings.countUnfinishedByRoom(id, LodgingBookingStatus.CHECKED_IN,
					LodgingBookingStatus.UPCOMING, now);
			if (unfinished > 0)
				throw new ConflictException("진행 중이거나 예정된 예약이 " + unfinished + "건 있어 객실 타입을 바꿀 수 없습니다.");
		}
		String code = r.roomCode().trim();
		if (repo.existsByRoomType_IdAndRoomCodeAndIdNot(target.getId(), code, id))
			throw new ConflictException("이 객실 타입에 이미 있는 객실 번호입니다: " + code);
		room.update(target, code, blankToNull(r.roomName()), CurrentUser.idOrNull(), now);
		return detail(room, now);
	}

	/** 수동 상태 변경. MAINTENANCE·CLOSED 는 입실 중인 예약이 있으면 409. */
	public RoomDetailResponse changeStatus(long id, LodgingRoomStatus status) {
		LodgingRoom room = lockRoom(id);
		LodgingRoomStatus before = room.getStatus();
		LocalDateTime now = now();
		if (before == status)
			return detail(room, now);
		if (!status.isBookable()) {
			List<String> checkedIn = bookings.findBookingNosByRoomAndStatus(id, LodgingBookingStatus.CHECKED_IN);
			if (!checkedIn.isEmpty())
				throw new ConflictException("입실 중인 예약(" + String.join(", ", checkedIn) + ")이 있어 " + status
						+ " 로 바꿀 수 없습니다. 퇴실 처리 후 다시 시도하세요.");
		}
		room.changeStatus(status, CurrentUser.idOrNull(), now);
		RoomDetailResponse res = detail(room, now);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("roomCode", room.getRoomCode());
		details.put("before", before);
		details.put("after", status);
		details.put("upcomingBookingCount", res.upcomingBookingCount());
		audit.log("STATUS_CHANGE", "lodging_room", id, details);
		return res;
	}

	/** 청소 완료: CLEANING → AVAILABLE */
	public RoomDetailResponse cleaned(long id) {
		LodgingRoom room = lockRoom(id);
		LocalDateTime now = now();
		room.markCleaned(CurrentUser.idOrNull(), now);
		return detail(room, now);
	}

	/**
	 * 가용 객실. OVERNIGHT: date 16:00 → 마지막 밤 다음 날 11:00(nights 박 모두 비어 있어야 함).
	 * DAY_USE: date checkInTime → + dayUseHours(생략 시 객실 타입의 대실 시간). 금액은 할인 전 기본 금액.
	 */
	@Transactional(readOnly = true)
	public List<AvailableRoomResponse> availability(Long golfCourseId, Long roomTypeId, LodgingBookingType bookingType,
			LocalDate date, LocalTime checkInTime, Short dayUseHours, Integer nights, Integer guestCount) {
		if (golfCourseId == null && roomTypeId == null)
			throw new IllegalArgumentException("골프장(golfCourseId) 또는 객실 타입(roomTypeId)을 지정하세요.");
		refs.requireIfPresent(Ref.GOLF_COURSE, golfCourseId);
		if (roomTypeId != null)
			findType(roomTypeId);
		int guests = guestCount == null ? 1 : guestCount;
		if (guests < 1)
			throw new IllegalArgumentException("이용 인원은 1명 이상이어야 합니다.");
		int n = nights == null ? 1 : nights;
		if (bookingType == LodgingBookingType.OVERNIGHT && (n < 1 || n > LodgingBookingService.MAX_NIGHTS))
			throw new IllegalArgumentException("숙박 일수는 1~" + LodgingBookingService.MAX_NIGHTS + "박이어야 합니다.");
		if (bookingType == LodgingBookingType.DAY_USE) {
			if (n != 1)
				throw new IllegalArgumentException("대실은 숙박 일수(nights)를 지정할 수 없습니다.");
			if (checkInTime == null)
				throw new IllegalArgumentException("대실 조회는 입실 시각(checkInTime)이 필요합니다.");
		}

		Specification<LodgingRoom> spec = Specification.allOf(Specs.eq("roomType.golfCourseId", golfCourseId),
				Specs.eq("roomType.id", roomTypeId), Specs.eq("roomType.active", true),
				Specs.in("status", List.of(LodgingRoomStatus.AVAILABLE, LodgingRoomStatus.CLEANING)),
				capacityAtLeast(guests));
		List<LodgingRoom> candidates = repo.findAll(spec, Sort.by("roomType.code", "roomCode", "id"));
		if (candidates.isEmpty())
			return List.of();

		// 객실마다 시간대 계산(대실 시간은 타입마다 다를 수 있음)
		LocalDateTime now = now();
		Map<Long, List<LodgingSlot>> slotsByRoom = new HashMap<>();
		LocalDateTime from = null;
		LocalDateTime to = null;
		for (LodgingRoom room : candidates) {
			List<LodgingSlot> slots = slots(bookingType, date, checkInTime,
					dayUseHours == null ? room.getRoomType().getDayUseHours() : dayUseHours, n);
			slotsByRoom.put(room.getId(), slots);
			LocalDateTime first = slots.get(0).checkIn();
			LocalDateTime last = slots.get(slots.size() - 1).checkOut();
			from = from == null || first.isBefore(from) ? first : from;
			to = to == null || last.isAfter(to) ? last : to;
		}
		if (!to.isAfter(now))
			throw new IllegalArgumentException("이미 지난 시간대는 조회할 수 없습니다.");

		Map<Long, List<LodgingBooking>> busy = new HashMap<>();
		for (LodgingBooking b : bookings.findOverlappingRooms(slotsByRoom.keySet(), LodgingBookingStatus.ACTIVE, from,
				to))
			busy.computeIfAbsent(b.getRoom().getId(), k -> new ArrayList<>()).add(b);

		List<AvailableRoomResponse> result = new ArrayList<>();
		for (LodgingRoom room : candidates) {
			List<LodgingSlot> slots = slotsByRoom.get(room.getId());
			List<LodgingBooking> taken = busy.getOrDefault(room.getId(), List.of());
			boolean free = slots.stream().noneMatch(
					s -> taken.stream().anyMatch(b -> s.overlaps(b.getScheduledCheckInAt(), b.getScheduledCheckOutAt())));
			if (!free)
				continue;
			LodgingSlot first = slots.get(0);
			BigDecimal rate = room.getRoomType().rateFor(bookingType);
			result.add(new AvailableRoomResponse(RoomResponse.of(room), bookingType, first.checkIn(),
					slots.get(slots.size() - 1).checkOut(), (short) (bookingType == LodgingBookingType.OVERNIGHT ? n : 0),
					first.dayUseHours(), rate, rate.multiply(BigDecimal.valueOf(slots.size()))));
		}
		return result;
	}

	/** 연박은 박마다 한 시간대(date+i 16:00 → date+i+1 11:00), 대실은 한 시간대 */
	static List<LodgingSlot> slots(LodgingBookingType type, LocalDate date, LocalTime checkInTime, int dayUseHours,
			int nights) {
		if (type == LodgingBookingType.DAY_USE)
			return List.of(LodgingSlot.dayUse(date, checkInTime, dayUseHours));
		List<LodgingSlot> slots = new ArrayList<>(nights);
		for (int i = 0; i < nights; i++)
			slots.add(LodgingSlot.overnight(date.plusDays(i)));
		return slots;
	}

	/** Specs.gte 는 중첩 속성을 지원하지 않아 직접 만든다. */
	private static Specification<LodgingRoom> capacityAtLeast(int guests) {
		return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("roomType").<Integer>get("capacity"), guests);
	}

	private RoomDetailResponse detail(LodgingRoom room, LocalDateTime now) {
		if (room.getId() == null)
			return new RoomDetailResponse(RoomResponse.of(room), null, 0);
		List<String> checkedIn = bookings.findBookingNosByRoomAndStatus(room.getId(), LodgingBookingStatus.CHECKED_IN);
		long upcoming = bookings.countUpcomingByRoom(room.getId(), LodgingBookingStatus.UPCOMING, now);
		return new RoomDetailResponse(RoomResponse.of(room), checkedIn.isEmpty() ? null : checkedIn.get(0), upcoming);
	}

	private LodgingRoom find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of(Ref.LODGING_ROOM.label, id));
	}

	private LodgingRoom lockRoom(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of(Ref.LODGING_ROOM.label, id));
	}

	private LodgingRoomType findType(long id) {
		return types.findById(id).orElseThrow(() -> NotFoundException.of(Ref.LODGING_ROOM_TYPE.label, id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
