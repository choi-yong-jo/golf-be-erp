package com.golf.erp.lodging;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.lodging.LodgingDtos.RoomTypeCreateRequest;
import com.golf.erp.lodging.LodgingDtos.RoomTypeDetailResponse;
import com.golf.erp.lodging.LodgingDtos.RoomTypeResponse;
import com.golf.erp.lodging.LodgingDtos.RoomTypeUpdateRequest;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 객실 타입(lodging_room_type): 목록·상세·등록·수정·활성/비활성.
 * 타입 코드는 골프장 안에서 유일하고 대문자로 저장한다. 골프장·코드는 등록 뒤 바꾸지 않는다.
 */
@Service
@Transactional
public class LodgingRoomTypeService {
	private static final int DEFAULT_CAPACITY = 2;
	private static final short DEFAULT_DAY_USE_HOURS = 3;

	private final LodgingRoomTypeRepository repo;
	private final LodgingRoomRepository rooms;
	private final LodgingBookingRepository bookings;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public LodgingRoomTypeService(LodgingRoomTypeRepository repo, LodgingRoomRepository rooms,
			LodgingBookingRepository bookings, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.rooms = rooms;
		this.bookings = bookings;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** 건수가 적은 마스터라 목록 전체를 돌려준다. keyword: 타입 코드·이름. 골프장·코드 순. */
	@Transactional(readOnly = true)
	public List<RoomTypeResponse> list(Long golfCourseId, Boolean active, String keyword) {
		Specification<LodgingRoomType> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("active", active), Specs.like(keyword, "code", "name"));
		List<LodgingRoomType> types = repo.findAll(spec, Sort.by("golfCourseId", "code"));
		Map<Long, Long> counts = new HashMap<>();
		if (!types.isEmpty())
			for (Object[] row : rooms.countByRoomTypeIds(types.stream().map(LodgingRoomType::getId).toList()))
				counts.put((Long) row[0], (Long) row[1]);
		return types.stream().map(t -> RoomTypeResponse.of(t, counts.getOrDefault(t.getId(), 0L))).toList();
	}

	@Transactional(readOnly = true)
	public RoomTypeDetailResponse get(long id) {
		return detail(find(id));
	}

	public RoomTypeDetailResponse create(RoomTypeCreateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		String code = r.roomTypeCode().trim().toUpperCase(Locale.ROOT);
		if (repo.existsByGolfCourseIdAndCode(r.golfCourseId(), code))
			throw new ConflictException("이미 있는 객실 타입 코드입니다: " + code);
		LocalDateTime now = now();
		Long actor = CurrentUser.idOrNull();
		LodgingRoomType t = new LodgingRoomType(r.golfCourseId(), code, actor, now);
		t.update(r.roomTypeName().trim(), r.capacity() == null ? DEFAULT_CAPACITY : r.capacity(),
				r.dayUseHours() == null ? DEFAULT_DAY_USE_HOURS : r.dayUseHours(), won(r.dayUseBaseFee(), "대실 요금"),
				won(r.overnightBaseFee(), "1박 요금"), actor, now);
		return detail(repo.save(t));
	}

	/** 이름·정원·대실 시간·요금. 이미 받은 예약은 예약에 저장된 시간·금액을 그대로 쓴다. */
	public RoomTypeDetailResponse update(long id, RoomTypeUpdateRequest r) {
		LodgingRoomType t = find(id);
		t.update(r.roomTypeName().trim(), r.capacity(), r.dayUseHours(), won(r.dayUseBaseFee(), "대실 요금"),
				won(r.overnightBaseFee(), "1박 요금"), CurrentUser.idOrNull(), now());
		return detail(t);
	}

	public RoomTypeDetailResponse activate(long id) {
		LodgingRoomType t = find(id);
		if (!t.isActive()) {
			t.activate(CurrentUser.idOrNull(), now());
			audit.log("ACTIVATE", "lodging_room_type", id, Map.of("roomTypeCode", t.getCode()));
		}
		return detail(t);
	}

	/**
	 * 비활성화. 이 타입의 객실을 모두 잠근 뒤(예약 등록과 직렬화) 끝나지 않은 예약(입실 중, 또는 입실 전이고 퇴실 예정 시각이
	 * 지나지 않은 것)이 있으면 409.
	 */
	public RoomTypeDetailResponse deactivate(long id) {
		LodgingRoomType t = find(id);
		if (!t.isActive())
			return detail(t);
		rooms.lockByRoomTypeId(id);
		LocalDateTime now = now();
		long unfinished = bookings.countUnfinishedByRoomType(id, LodgingBookingStatus.CHECKED_IN,
				LodgingBookingStatus.UPCOMING, now);
		if (unfinished > 0)
			throw new ConflictException("이 객실 타입에 진행 중이거나 예정된 예약이 " + unfinished
					+ "건 있습니다. 예약을 처리(퇴실·취소)한 뒤 비활성화하세요.");
		t.deactivate(CurrentUser.idOrNull(), now);
		audit.log("DEACTIVATE", "lodging_room_type", id, Map.of("roomTypeCode", t.getCode()));
		return detail(t);
	}

	private RoomTypeDetailResponse detail(LodgingRoomType t) {
		Map<LodgingRoomStatus, Long> byStatus = new EnumMap<>(LodgingRoomStatus.class);
		for (LodgingRoomStatus s : LodgingRoomStatus.values())
			byStatus.put(s, 0L);
		long total = 0;
		if (t.getId() != null)
			for (Object[] row : rooms.countByStatus(t.getId())) {
				long c = (Long) row[1];
				byStatus.put((LodgingRoomStatus) row[0], c);
				total += c;
			}
		return new RoomTypeDetailResponse(RoomTypeResponse.of(t, total), byStatus);
	}

	private LodgingRoomType find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of(Ref.LODGING_ROOM_TYPE.label, id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	/** 원 단위 금액(생략 시 0). DTO 검증(@Digits fraction=0)을 거친 값을 scale 2 로 맞춘다. */
	static BigDecimal won(BigDecimal v, String label) {
		if (v == null)
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		if (v.signum() < 0)
			throw new IllegalArgumentException(label + "은(는) 0 이상이어야 합니다.");
		if (v.stripTrailingZeros().scale() > 0)
			throw new IllegalArgumentException(label + "은(는) 원 단위(소수 없음)로 입력하세요.");
		return v.setScale(2, RoundingMode.HALF_UP);
	}
}
