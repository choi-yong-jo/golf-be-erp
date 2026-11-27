package com.golf.erp.facility;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.facility.FacilityDtos.FacilityCreateRequest;
import com.golf.erp.facility.FacilityDtos.FacilityResponse;
import com.golf.erp.facility.FacilityDtos.FacilityUpdateRequest;
import com.golf.erp.facility.FacilityDtos.UnitCreateRequest;
import com.golf.erp.facility.FacilityDtos.UnitResponse;
import com.golf.erp.facility.FacilityDtos.UnitStatusResponse;
import com.golf.erp.facility.FacilityDtos.UnitUpdateRequest;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부대시설(facility)과 공간(facility_unit) 관리. 공간 종류는 {@link FacilityRoomTypeService}.
 * <p>
 * 잠금 순서: 시설 → 공간, 공간 종류 → 공간 → 예약. 반대 순서로 잡지 않는다.
 */
@Service
@Transactional
public class FacilityService {
	private final FacilityRepository facilities;
	private final FacilityRoomTypeRepository roomTypes;
	private final FacilityUnitRepository units;
	private final FacilityBookingRepository bookings;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public FacilityService(FacilityRepository facilities, FacilityRoomTypeRepository roomTypes,
			FacilityUnitRepository units, FacilityBookingRepository bookings, RefChecker refs, AuditLogger audit,
			Clock clock) {
		this.facilities = facilities;
		this.roomTypes = roomTypes;
		this.units = units;
		this.bookings = bookings;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- 시설

	/** keyword: 시설 코드·이름 */
	@Transactional(readOnly = true)
	public PageResult<FacilityResponse> list(Long golfCourseId, FacilityType type, Boolean active, String keyword,
			int page, int size) {
		Specification<Facility> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("type", type), Specs.eq("active", active), Specs.like(keyword, "code", "name"));
		Page<Facility> result = facilities.findAll(spec, Pages.of(page, size, Sort.by("golfCourseId", "code")));
		List<Long> ids = result.getContent().stream().map(Facility::getId).toList();
		Map<Long, Long> typeCounts = new HashMap<>();
		Map<Long, Long> unitCounts = new HashMap<>();
		if (!ids.isEmpty()) {
			for (Object[] row : roomTypes.countByFacilityIds(ids))
				typeCounts.put((Long) row[0], (Long) row[1]);
			for (Object[] row : units.countByFacilityIds(ids))
				unitCounts.put((Long) row[0], (Long) row[1]);
		}
		return PageResult.of(result, f -> FacilityResponse.of(f, typeCounts.getOrDefault(f.getId(), 0L),
				unitCounts.getOrDefault(f.getId(), 0L)));
	}

	@Transactional(readOnly = true)
	public FacilityResponse get(long id) {
		return response(findFacility(id));
	}

	public FacilityResponse create(FacilityCreateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		String code = r.code().trim();
		if (facilities.existsByGolfCourseIdAndCode(r.golfCourseId(), code))
			throw new ConflictException("이미 있는 시설 코드입니다: " + code);
		Facility f = new Facility(r.golfCourseId(), code, r.name().trim(), r.type(), !Boolean.FALSE.equals(r.active()),
				CurrentUser.idOrNull(), now());
		return FacilityResponse.of(facilities.save(f), 0, 0);
	}

	/** 두 유형 모두 시간 단위 요금이라 유형 변경이 기존 예약 금액에 영향을 주지 않는다. */
	public FacilityResponse update(long id, FacilityUpdateRequest r) {
		Facility f = findFacility(id);
		String code = r.code().trim();
		if (facilities.existsByGolfCourseIdAndCodeAndIdNot(f.getGolfCourseId(), code, id))
			throw new ConflictException("이미 있는 시설 코드입니다: " + code);
		f.update(code, r.name().trim(), r.type(), CurrentUser.idOrNull(), now());
		return response(f);
	}

	public FacilityResponse activate(long id) {
		Facility f = facilities.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("시설", id));
		if (!f.isActive()) {
			f.activate(CurrentUser.idOrNull(), now());
			audit.log("ACTIVATE", "facility", id, Map.of("code", f.getCode()));
		}
		return response(f);
	}

	/**
	 * 비활성화. 소속 공간에 끝나지 않은 예약(입실 전이면서 종료 전, 또는 입실 중)이 있으면 409.
	 * 시설과 소속 공간을 모두 잠가 동시에 들어오는 예약 등록과 직렬화한다.
	 */
	public FacilityResponse deactivate(long id, String reason) {
		Facility f = facilities.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("시설", id));
		units.lockByFacilityId(id);
		if (f.isActive()) {
			long n = bookings.countUnfinishedByFacility(id, FacilityBookingStatus.CHECKED_IN,
					FacilityBookingStatus.UPCOMING, now());
			if (n > 0)
				throw new ConflictException(
						"끝나지 않은 예약이 " + n + "건 있어 시설을 비활성화할 수 없습니다. 예약을 먼저 취소하거나 퇴실 처리하세요.");
			f.deactivate(CurrentUser.idOrNull(), now());
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("code", f.getCode());
			String why = blankToNull(reason);
			if (why != null)
				details.put("reason", why);
			audit.log("DEACTIVATE", "facility", id, details);
		}
		return response(f);
	}

	// ---------------------------------------------------------------- 공간

	/** type: 시설 유형. keyword: 공간 코드·이름 */
	@Transactional(readOnly = true)
	public PageResult<UnitResponse> listUnits(Long golfCourseId, Long facilityId, Long facilityRoomTypeId,
			FacilityUnitStatus status, FacilityType type, String keyword, int page, int size) {
		Specification<FacilityUnit> spec = Specification.allOf(Specs.eq("facility.golfCourseId", golfCourseId),
				Specs.eq("facility.id", facilityId), Specs.eq("roomType.id", facilityRoomTypeId),
				Specs.eq("status", status), Specs.eq("facility.type", type),
				Specs.like(keyword, "unitCode", "unitName"));
		return PageResult.of(units.findAll(spec, Pages.of(page, size, Sort.by("facility.id", "unitCode"))),
				UnitResponse::of);
	}

	@Transactional(readOnly = true)
	public List<UnitResponse> unitsOf(long facilityId) {
		findFacility(facilityId);
		return units.findByFacilityId(facilityId).stream().map(UnitResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public UnitResponse getUnit(long id) {
		return UnitResponse.of(units.findById(id).orElseThrow(() -> NotFoundException.of("공간", id)));
	}

	/**
	 * 공간 등록. 공간 종류는 같은 시설의 사용 중인 것(다른 시설 400, 사용 중지 409). 공간 종류 행을 잠가
	 * 동시에 들어오는 종류 비활성화와 직렬화한다. capacity 생략 시 종류의 기본 수용 인원.
	 */
	public UnitResponse createUnit(UnitCreateRequest r) {
		Facility f = findFacility(r.facilityId());
		FacilityRoomType t = lockRoomType(r.facilityRoomTypeId());
		requireSameFacility(t, f.getId());
		if (!t.isActive())
			throw new ConflictException("사용 중지된 공간 종류에는 공간을 추가할 수 없습니다: " + t.getName());
		String code = r.unitCode().trim();
		if (units.existsByFacility_IdAndUnitCode(f.getId(), code))
			throw new ConflictException("이 시설에 이미 있는 공간 코드입니다: " + code);
		int capacity = r.capacity() == null ? t.getDefaultCapacity() : r.capacity();
		FacilityUnit u = new FacilityUnit(f, t, code, r.unitName().trim(), capacity, CurrentUser.idOrNull(), now());
		return UnitResponse.of(units.save(u));
	}

	/**
	 * 공간 수정. 공간 종류는 같은 시설의 것으로만 바꿀 수 있고(400), 다른 종류로 바꿀 때 그 종류가 사용 중지면 409.
	 * 이미 받은 예약은 요금 스냅샷을 유지한다. 수용 인원을 끝나지 않은 예약의 인원보다 작게 줄일 수는 없다(409).
	 * capacity 생략 시 (바뀐) 종류의 기본 수용 인원. 잠금 순서: 공간 종류 → 공간.
	 */
	public UnitResponse updateUnit(long id, UnitUpdateRequest r) {
		long facilityId = units.findFacilityIdById(id).orElseThrow(() -> NotFoundException.of("공간", id));
		FacilityRoomType t = lockRoomType(r.facilityRoomTypeId());
		requireSameFacility(t, facilityId);
		FacilityUnit u = lockUnit(id);
		boolean typeChanged = !u.getRoomType().getId().equals(t.getId());
		if (typeChanged && !t.isActive())
			throw new ConflictException("사용 중지된 공간 종류로 바꿀 수 없습니다: " + t.getName());
		String code = r.unitCode().trim();
		if (units.existsByFacility_IdAndUnitCodeAndIdNot(facilityId, code, id))
			throw new ConflictException("이 시설에 이미 있는 공간 코드입니다: " + code);
		int capacity = r.capacity() == null ? t.getDefaultCapacity() : r.capacity();
		LocalDateTime now = now();
		Integer maxGuests = bookings.maxUnfinishedGuestCount(id, FacilityBookingStatus.CHECKED_IN,
				FacilityBookingStatus.UPCOMING, now);
		if (maxGuests != null && capacity < maxGuests)
			throw new ConflictException(
					"끝나지 않은 예약 중 " + maxGuests + "명 예약이 있어 수용 인원을 " + capacity + "명으로 줄일 수 없습니다.");
		Long beforeTypeId = u.getRoomType().getId();
		u.update(t, code, r.unitName().trim(), capacity, CurrentUser.idOrNull(), now);
		if (typeChanged) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("unitCode", code);
			details.put("beforeRoomTypeId", beforeTypeId);
			details.put("afterRoomTypeId", t.getId());
			audit.log("ROOM_TYPE_CHANGE", "facility_unit", id, details);
		}
		return UnitResponse.of(u);
	}

	/**
	 * 운영 상태 변경. MAINTENANCE·CLOSED 로는 입실 중(CHECKED_IN) 예약이 있으면 바꿀 수 없다(409).
	 * 입실 전 예약은 취소하지 않고 건수만 응답(upcomingBookingCount, warning)에 알려 준다.
	 */
	public UnitStatusResponse changeUnitStatus(long id, FacilityUnitStatus status, String reason) {
		FacilityUnit u = lockUnit(id);
		FacilityUnitStatus before = u.getStatus();
		LocalDateTime now = now();
		if (status != FacilityUnitStatus.AVAILABLE) {
			List<String> inHouse = bookings.findBookingNosByUnitAndStatus(id, FacilityBookingStatus.CHECKED_IN);
			if (!inHouse.isEmpty())
				throw new ConflictException("입실 중인 예약(" + String.join(", ", inHouse) + ")이 있어 " + status
						+ " 로 바꿀 수 없습니다. 퇴실 처리 후 다시 시도하세요.");
		}
		long upcoming = bookings.countByUnit_IdAndStatusInAndEndAtAfter(id, FacilityBookingStatus.UPCOMING, now);
		String warning = null;
		if (status != FacilityUnitStatus.AVAILABLE && upcoming > 0)
			warning = "입실 전 예약 " + upcoming + "건이 남아 있습니다. 자동으로 취소되지 않으니 다른 공간으로 옮기거나 취소하세요.";
		if (before != status) {
			u.changeStatus(status, CurrentUser.idOrNull(), now);
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("before", before);
			details.put("after", status);
			details.put("upcomingBookingCount", upcoming);
			String why = blankToNull(reason);
			if (why != null)
				details.put("reason", why);
			audit.log("STATUS_CHANGE", "facility_unit", id, details);
		}
		return new UnitStatusResponse(UnitResponse.of(u), upcoming, warning);
	}

	// ---------------------------------------------------------------- 내부

	private FacilityResponse response(Facility f) {
		return FacilityResponse.of(f, roomTypes.countByFacility_Id(f.getId()), units.countByFacility_Id(f.getId()));
	}

	/** 공간 종류가 그 시설 소속이 아니면 400 */
	static void requireSameFacility(FacilityRoomType t, long facilityId) {
		if (t.getFacility().getId() != facilityId)
			throw new IllegalArgumentException(
					"공간 종류(" + t.getName() + ")는 이 시설(id " + facilityId + ")에 속하지 않습니다. 같은 시설의 공간 종류를 선택하세요.");
	}

	private Facility findFacility(long id) {
		return facilities.findById(id).orElseThrow(() -> NotFoundException.of("시설", id));
	}

	private FacilityRoomType lockRoomType(long id) {
		return roomTypes.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("공간 종류", id));
	}

	private FacilityUnit lockUnit(long id) {
		return units.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("공간", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
