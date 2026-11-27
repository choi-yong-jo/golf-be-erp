package com.golf.erp.lodging;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.lodging.LodgingDtos.BookingCreateRequest;
import com.golf.erp.lodging.LodgingDtos.BookingCreateResponse;
import com.golf.erp.lodging.LodgingDtos.BookingResponse;
import com.golf.erp.lodging.LodgingDtos.BookingUpdateRequest;
import com.golf.erp.lodging.LodgingDtos.CalendarBooking;
import com.golf.erp.lodging.LodgingDtos.CalendarResponse;
import com.golf.erp.lodging.LodgingDtos.CalendarRoom;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 숙박 예약(lodging_booking): 목록·상세·달력, 등록·수정, 확정·입실·퇴실·취소·노쇼, 수납 상태.
 * <p>
 * <b>예약 한 행 = 대실 1회 또는 1박</b> (DDL ck_lodging_booking_mode): OVERNIGHT 는 stay_nights = 1, 16:00 입실 →
 * 다음 날 11:00 퇴실로 고정이라 연박(nights &gt; 1)은 같은 객실·손님으로 박마다 한 행씩 한 트랜잭션에서 만든다.
 * 각 행은 따로 취소·입실·퇴실한다.
 * <p>
 * <b>금액</b>: rate_amount = 객실 타입의 대실 요금 또는 1박 요금(등록 시점 스냅샷, 대실 시간과 무관한 1회 요금),
 * base_amount = rate_amount, total = base − discount (discount ≤ base). 수정해도 요금은 바꾸지 않는다.
 * <p>
 * <b>겹침</b>: 같은 객실의 점유 예약(PENDING·CONFIRMED·CHECKED_IN)과 [입실 예정, 퇴실 예정) 이 겹치면 409.
 * DB 배타 제약이 없으므로 검사 전에 객실 행을 PESSIMISTIC_WRITE 로 잠근다. 잠금 순서는 항상 객실 → 예약.
 * <p>
 * <b>하우스키핑</b>: 퇴실 처리 시 객실이 AVAILABLE 이면 CLEANING 으로 바뀐다. CLEANING 객실은 미래 예약은 받지만
 * 청소 완료 전에는 입실 처리할 수 없다.
 */
@Service
@Transactional
public class LodgingBookingService {
	/** 한 번에 등록할 수 있는 최대 연박 수 */
	public static final int MAX_NIGHTS = 14;
	/** 달력 조회 최대 기간(일, from·to 포함) */
	static final int MAX_CALENDAR_DAYS = 62;

	private final LodgingBookingRepository repo;
	private final LodgingRoomRepository rooms;
	private final LodgingRoomTypeRepository types;
	private final LodgingLookup lookup;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public LodgingBookingService(LodgingBookingRepository repo, LodgingRoomRepository rooms,
			LodgingRoomTypeRepository types, LodgingLookup lookup, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.rooms = rooms;
		this.types = types;
		this.lookup = lookup;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- 조회

	/**
	 * from/to: 예약 기간이 [from 0시, to 다음 날 0시) 와 겹치는 것. keyword: 손님 이름·전화번호·예약 번호.
	 * 입실 예정 시각 최근 순.
	 */
	@Transactional(readOnly = true)
	public PageResult<BookingResponse> list(Long golfCourseId, Long roomTypeId, Long roomId,
			LodgingBookingType bookingType, LodgingBookingStatus status, LodgingPaymentStatus paymentStatus,
			Long memberId, LocalDate from, LocalDate to, String keyword, int page, int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일이 시작일보다 앞설 수 없습니다.");
		Specification<LodgingBooking> spec = Specification.allOf(
				Specs.eq("room.roomType.golfCourseId", golfCourseId), Specs.eq("room.roomType.id", roomTypeId),
				Specs.eq("room.id", roomId), Specs.eq("bookingType", bookingType), Specs.eq("status", status),
				Specs.eq("paymentStatus", paymentStatus), Specs.eq("memberId", memberId),
				Specs.like(keyword, "guestName", "guestPhone", "bookingNo"),
				from == null ? null : endsAfter(from.atStartOfDay()),
				Specs.lt("scheduledCheckInAt", to == null ? null : to.plusDays(1).atStartOfDay()));
		Sort sort = Sort.by(Sort.Direction.DESC, "scheduledCheckInAt", "id");
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, sort)), BookingResponse::of);
	}

	@Transactional(readOnly = true)
	public BookingResponse get(long id) {
		return BookingResponse.of(find(id));
	}

	/**
	 * 객실별 예약 달력(from~to 포함, 최대 {@value #MAX_CALENDAR_DAYS}일). 골프장 또는 객실 타입으로 객실을 고르고,
	 * 취소·노쇼를 뺀 예약(PENDING·CONFIRMED·CHECKED_IN·CHECKED_OUT)을 객실마다 붙인다.
	 */
	@Transactional(readOnly = true)
	public CalendarResponse calendar(Long golfCourseId, Long roomTypeId, LocalDate from, LocalDate to) {
		if (golfCourseId == null && roomTypeId == null)
			throw new IllegalArgumentException("골프장(golfCourseId) 또는 객실 타입(roomTypeId)을 지정하세요.");
		if (to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일이 시작일보다 앞설 수 없습니다.");
		if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS)
			throw new IllegalArgumentException("달력 조회 기간은 최대 " + MAX_CALENDAR_DAYS + "일입니다.");
		refs.requireIfPresent(Ref.GOLF_COURSE, golfCourseId);
		if (roomTypeId != null && !types.existsById(roomTypeId))
			throw NotFoundException.of(Ref.LODGING_ROOM_TYPE.label, roomTypeId);

		LocalDateTime start = from.atStartOfDay();
		LocalDateTime end = to.plusDays(1).atStartOfDay();
		Specification<LodgingBooking> bookingSpec = Specification.allOf(
				Specs.eq("room.roomType.golfCourseId", golfCourseId), Specs.eq("room.roomType.id", roomTypeId),
				Specs.in("status", LodgingBookingStatus.CALENDAR), endsAfter(start),
				Specs.lt("scheduledCheckInAt", end));
		Map<Long, List<CalendarBooking>> byRoom = new HashMap<>();
		for (LodgingBooking b : repo.findAll(bookingSpec, Sort.by("scheduledCheckInAt", "id")))
			byRoom.computeIfAbsent(b.getRoom().getId(), k -> new ArrayList<>()).add(CalendarBooking.of(b));

		Specification<LodgingRoom> roomSpec = Specification.allOf(Specs.eq("roomType.golfCourseId", golfCourseId),
				Specs.eq("roomType.id", roomTypeId));
		List<CalendarRoom> roomRows = rooms.findAll(roomSpec, Sort.by("roomType.code", "roomCode", "id")).stream()
				.map(r -> new CalendarRoom(r.getId(), r.getRoomCode(), r.getRoomName(), r.getRoomType().getId(),
						r.getRoomType().getCode(), r.getRoomType().getName(), r.getStatus(),
						byRoom.getOrDefault(r.getId(), List.of())))
				.toList();
		return new CalendarResponse(golfCourseId, roomTypeId, from, to, roomRows);
	}

	// ---------------------------------------------------------------- 등록·수정

	/**
	 * 예약 등록. 객실을 잠근 뒤 객실 상태·타입 활성·정원·겹침을 확인한다. OVERNIGHT 연박은 박마다 한 행.
	 * 번호는 LB-yyyyMMdd-000123.
	 */
	public BookingCreateResponse create(BookingCreateRequest r) {
		refs.requireIfPresent(Ref.MEMBER, r.memberId());
		int nights = r.nights() == null ? 1 : r.nights();
		if (r.bookingType() == LodgingBookingType.DAY_USE && nights != 1)
			throw new IllegalArgumentException("대실은 숙박 일수(nights)를 지정할 수 없습니다.");
		if (nights < 1 || nights > MAX_NIGHTS)
			throw new IllegalArgumentException("숙박 일수는 1~" + MAX_NIGHTS + "박이어야 합니다.");

		LodgingRoom room = lockRoom(r.lodgingRoomId());
		room.requireBookable();
		LodgingRoomType t = room.getRoomType();
		requireCapacity(t, r.guestCount());
		int hours = r.dayUseHours() == null ? t.getDayUseHours() : r.dayUseHours();
		List<LodgingSlot> slots = LodgingRoomService.slots(r.bookingType(), r.date(), r.checkInTime(), hours, nights);
		LocalDateTime now = now();
		if (!slots.get(0).checkOut().isAfter(now))
			throw new IllegalArgumentException("이미 지난 시간대는 예약할 수 없습니다.");
		for (LodgingSlot s : slots)
			requireNoOverlap(room.getId(), s, 0L);

		Guest guest = guest(r.guestName(), r.guestPhone(), r.memberId());
		BigDecimal rate = t.rateFor(r.bookingType()).setScale(2, RoundingMode.HALF_UP);
		BigDecimal discount = discountAmount(r.discountAmount(), rate);
		LodgingBookingStatus initial = Boolean.TRUE.equals(r.tentative()) ? LodgingBookingStatus.PENDING
				: LodgingBookingStatus.CONFIRMED;
		String request = blankToNull(r.specialRequest());
		Long actor = CurrentUser.idOrNull();

		List<BookingResponse> created = new ArrayList<>(slots.size());
		BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		for (LodgingSlot s : slots) {
			LodgingBooking b = new LodgingBooking(DocumentNo.temporary(), room, r.memberId(), initial, s, rate, actor,
					now);
			b.changeDetails(guest.name(), guest.phone(), s, discount, request, now);
			repo.saveAndFlush(b);
			b.assignNo(DocumentNo.of("LB", now.toLocalDate(), b.getId()));
			created.add(BookingResponse.of(b));
			total = total.add(b.getTotalAmount());
		}
		return new BookingCreateResponse(created.size(), total, created);
	}

	/**
	 * 날짜·시간·손님·할인·요청 사항 수정(입실 전만). 시간대가 바뀌면 객실 상태·타입 활성·겹침을 다시 확인한다.
	 * 객실·예약 유형·요금은 바꾸지 않는다.
	 */
	public BookingResponse update(long id, BookingUpdateRequest r) {
		Long roomId = repo.findRoomIdById(id).orElseThrow(() -> NotFoundException.of("숙박 예약", id));
		LodgingRoom room = lockRoom(roomId);
		LodgingBooking b = lock(id);
		b.requireEditable();
		LodgingRoomType t = room.getRoomType();
		requireCapacity(t, r.guestCount());
		int hours = r.dayUseHours() != null ? r.dayUseHours()
				: b.getDayUseHours() > 0 ? b.getDayUseHours() : t.getDayUseHours();
		LodgingSlot slot = LodgingRoomService.slots(b.getBookingType(), r.date(), r.checkInTime(), hours, 1).get(0);
		LocalDateTime now = now();
		boolean periodChanged = !slot.checkIn().equals(b.getScheduledCheckInAt())
				|| !slot.checkOut().equals(b.getScheduledCheckOutAt());
		if (periodChanged) {
			if (!slot.checkOut().isAfter(now))
				throw new IllegalArgumentException("이미 지난 시간대로 바꿀 수 없습니다.");
			room.requireBookable();
			requireNoOverlap(room.getId(), slot, id);
		}
		Guest guest = guest(r.guestName(), r.guestPhone(), b.getMemberId());
		BigDecimal discount = discountAmount(r.discountAmount(), b.getBaseAmount());
		b.changeDetails(guest.name(), guest.phone(), slot, discount, blankToNull(r.specialRequest()), now);
		return BookingResponse.of(b);
	}

	// ---------------------------------------------------------------- 상태 변경

	public BookingResponse confirm(long id) {
		LodgingBooking b = lock(id);
		b.confirm(now());
		return BookingResponse.of(b);
	}

	/**
	 * 입실(CONFIRMED → CHECKED_IN, actual_check_in_at = 지금). 예약일 0시부터 퇴실 예정 시각 전까지.
	 * 객실이 청소 중·보수·운영 중지이거나, 같은 객실에 아직 퇴실하지 않은 손님이 있으면 409.
	 */
	public BookingResponse checkIn(long id) {
		Long roomId = repo.findRoomIdById(id).orElseThrow(() -> NotFoundException.of("숙박 예약", id));
		LodgingRoom room = lockRoom(roomId);
		LodgingBooking b = lock(id);
		if (room.getStatus() == LodgingRoomStatus.CLEANING)
			throw new ConflictException("청소 중인 객실입니다. 청소 완료 처리 후 입실하세요: " + room.label());
		if (!room.getStatus().isBookable())
			throw new ConflictException("입실할 수 없는 객실 상태입니다(" + room.getStatus() + "): " + room.label());
		List<String> occupied = new ArrayList<>(
				repo.findBookingNosByRoomAndStatus(roomId, LodgingBookingStatus.CHECKED_IN));
		occupied.remove(b.getBookingNo());
		if (!occupied.isEmpty())
			throw new ConflictException("이 객실에 아직 퇴실하지 않은 예약(" + String.join(", ", occupied) + ")이 있습니다.");
		b.checkIn(now());
		return BookingResponse.of(b);
	}

	/** 퇴실(CHECKED_IN → CHECKED_OUT, actual_check_out_at = 지금). 객실은 CLEANING 이 된다. */
	public BookingResponse checkOut(long id) {
		Long roomId = repo.findRoomIdById(id).orElseThrow(() -> NotFoundException.of("숙박 예약", id));
		LodgingRoom room = lockRoom(roomId);
		LodgingBooking b = lock(id);
		LocalDateTime now = now();
		b.checkOut(now);
		room.startCleaning(CurrentUser.idOrNull(), now);
		return BookingResponse.of(b);
	}

	public BookingResponse cancel(long id, String reason) {
		LodgingBooking b = lock(id);
		LodgingBookingStatus before = b.getStatus();
		String why = blankToNull(reason);
		b.cancel(why, now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("bookingNo", b.getBookingNo());
		details.put("before", before);
		details.put("paymentStatus", b.getPaymentStatus());
		if (why != null)
			details.put("reason", why);
		audit.log("CANCEL", "lodging_booking", id, details);
		return BookingResponse.of(b);
	}

	public BookingResponse noShow(long id) {
		LodgingBooking b = lock(id);
		b.markNoShow(now());
		audit.log("NO_SHOW", "lodging_booking", id,
				Map.of("bookingNo", b.getBookingNo(), "paymentStatus", b.getPaymentStatus()));
		return BookingResponse.of(b);
	}

	/** 수납 상태 기록(결제 테이블 없음). 같은 값이면 아무것도 하지 않는다. */
	public BookingResponse changePaymentStatus(long id, LodgingPaymentStatus paymentStatus, String memo) {
		LodgingBooking b = lock(id);
		LodgingPaymentStatus before = b.getPaymentStatus();
		if (before != paymentStatus) {
			b.changePaymentStatus(paymentStatus, now());
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("bookingNo", b.getBookingNo());
			details.put("before", before);
			details.put("after", paymentStatus);
			details.put("totalAmount", b.getTotalAmount());
			String m = blankToNull(memo);
			if (m != null)
				details.put("memo", m);
			audit.log("PAYMENT_STATUS_CHANGE", "lodging_booking", id, details);
		}
		return BookingResponse.of(b);
	}

	// ---------------------------------------------------------------- 내부

	private record Guest(String name, String phone) {
	}

	/** 이름·전화가 비어 있으면 회원 정보로 채운다. 회원 예약이 아니면 이름은 필수(400). */
	private Guest guest(String name, String phone, Long memberId) {
		String n = blankToNull(name);
		String p = blankToNull(phone);
		if (memberId != null && (n == null || p == null)) {
			LodgingLookup.Contact c = lookup.member(memberId)
					.orElseThrow(() -> NotFoundException.of(Ref.MEMBER.label, memberId));
			if (n == null)
				n = c.name();
			if (p == null)
				p = truncate(c.phone(), 30);
		}
		if (n == null)
			throw new IllegalArgumentException("손님 이름(guestName)이 필요합니다. 회원 예약이면 memberId 를 주세요.");
		return new Guest(truncate(n, 100), p);
	}

	private static BigDecimal discountAmount(BigDecimal requested, BigDecimal base) {
		BigDecimal d = LodgingRoomTypeService.won(requested, "할인 금액");
		if (d.compareTo(base) > 0)
			throw new IllegalArgumentException("할인 금액(" + d + ")이 기본 금액(" + base + ")보다 클 수 없습니다.");
		return d;
	}

	/** guestCount 는 저장하지 않고 객실 타입 정원 확인에만 쓴다. */
	private static void requireCapacity(LodgingRoomType t, Integer guestCount) {
		if (guestCount != null && guestCount > t.getCapacity())
			throw new IllegalArgumentException(
					"이용 인원(" + guestCount + "명)이 객실 정원(" + t.getCapacity() + "명)을 넘습니다: " + t.getName());
	}

	/** 객실 행을 잠근 상태에서 호출해야 한다. */
	private void requireNoOverlap(long roomId, LodgingSlot slot, long excludeId) {
		List<LodgingBooking> overlaps = repo.findOverlapping(roomId, LodgingBookingStatus.ACTIVE, slot.checkIn(),
				slot.checkOut(), excludeId);
		if (!overlaps.isEmpty()) {
			LodgingBooking o = overlaps.get(0);
			throw new ConflictException("같은 객실에 겹치는 예약이 있습니다: " + o.getBookingNo() + " ("
					+ o.getScheduledCheckInAt() + " ~ " + o.getScheduledCheckOutAt() + ")");
		}
	}

	private static Specification<LodgingBooking> endsAfter(LocalDateTime at) {
		return (root, q, cb) -> cb.greaterThan(root.<LocalDateTime>get("scheduledCheckOutAt"), at);
	}

	private LodgingBooking find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("숙박 예약", id));
	}

	private LodgingBooking lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("숙박 예약", id));
	}

	private LodgingRoom lockRoom(long id) {
		return rooms.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of(Ref.LODGING_ROOM.label, id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
