package com.golf.erp.facility;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Specs;
import com.golf.erp.facility.FacilityDtos.RoomTypeCreateRequest;
import com.golf.erp.facility.FacilityDtos.RoomTypeResponse;
import com.golf.erp.facility.FacilityDtos.RoomTypeUpdateRequest;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 공간 종류(facility_room_type) 관리. 종류 코드는 시설 안에서 유일하며 대문자로 저장한다.
 * 시간당 기본요금은 원 단위 정수만 받는다. 요금을 바꿔도 이미 받은 예약의 요금 스냅샷은 바뀌지 않는다.
 * 사용 중지된 종류로는 새 공간을 만들거나 새 예약을 받을 수 없다.
 */
@Service
@Transactional
public class FacilityRoomTypeService {
	private final FacilityRoomTypeRepository repo;
	private final FacilityRepository facilities;
	private final FacilityUnitRepository units;
	private final FacilityBookingRepository bookings;
	private final AuditLogger audit;
	private final Clock clock;

	public FacilityRoomTypeService(FacilityRoomTypeRepository repo, FacilityRepository facilities,
			FacilityUnitRepository units, FacilityBookingRepository bookings, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.facilities = facilities;
		this.units = units;
		this.bookings = bookings;
		this.audit = audit;
		this.clock = clock;
	}

	/** 시설·사용 여부로 거른 공간 종류 목록(시설, 종류 코드 순, 페이지 없음) */
	@Transactional(readOnly = true)
	public List<RoomTypeResponse> list(Long golfCourseId, Long facilityId, Boolean active) {
		Specification<FacilityRoomType> spec = Specification.allOf(Specs.eq("facility.golfCourseId", golfCourseId),
				Specs.eq("facility.id", facilityId), Specs.eq("active", active));
		return withUnitCounts(repo.findAll(spec, Sort.by("facility.id", "code")));
	}

	/** 시설 한 곳의 공간 종류(종류 코드 순). 시설이 없으면 404 */
	@Transactional(readOnly = true)
	public List<RoomTypeResponse> listOfFacility(long facilityId, Boolean active) {
		if (!facilities.existsById(facilityId))
			throw NotFoundException.of("시설", facilityId);
		return list(null, facilityId, active);
	}

	@Transactional(readOnly = true)
	public RoomTypeResponse get(long id) {
		FacilityRoomType t = repo.findById(id).orElseThrow(() -> NotFoundException.of("공간 종류", id));
		return RoomTypeResponse.of(t, units.countByRoomType_Id(id));
	}

	public RoomTypeResponse create(RoomTypeCreateRequest r) {
		Facility f = facilities.findById(r.facilityId())
				.orElseThrow(() -> NotFoundException.of("시설", r.facilityId()));
		String code = normalizeCode(r.roomTypeCode());
		if (repo.existsByFacility_IdAndCode(f.getId(), code))
			throw new ConflictException("이 시설에 이미 있는 공간 종류 코드입니다: " + code);
		int capacity = r.defaultCapacity() == null ? 1 : r.defaultCapacity();
		FacilityRoomType t = new FacilityRoomType(f, code, r.roomTypeName().trim(), capacity, r.hourlyBaseRate(),
				!Boolean.FALSE.equals(r.active()), CurrentUser.idOrNull(), now());
		return RoomTypeResponse.of(repo.save(t), 0);
	}

	/** 요금·기본 수용 인원 변경은 기존 공간의 수용 인원과 기존 예약 금액에 영향을 주지 않는다. */
	public RoomTypeResponse update(long id, RoomTypeUpdateRequest r) {
		FacilityRoomType t = lock(id);
		String code = normalizeCode(r.roomTypeCode());
		if (repo.existsByFacility_IdAndCodeAndIdNot(t.getFacility().getId(), code, id))
			throw new ConflictException("이 시설에 이미 있는 공간 종류 코드입니다: " + code);
		var beforeRate = t.getHourlyBaseRate();
		t.update(code, r.roomTypeName().trim(), r.defaultCapacity(), r.hourlyBaseRate(), CurrentUser.idOrNull(),
				now());
		if (beforeRate.compareTo(t.getHourlyBaseRate()) != 0) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("code", code);
			details.put("beforeHourlyBaseRate", beforeRate);
			details.put("afterHourlyBaseRate", t.getHourlyBaseRate());
			audit.log("RATE_CHANGE", "facility_room_type", id, details);
		}
		return RoomTypeResponse.of(t, units.countByRoomType_Id(id));
	}

	/**
	 * 사용·중지 전환. 중지하려면 이 종류의 공간에 끝나지 않은 예약(입실 전이면서 종료 전, 또는 입실 중)이 없어야 한다(409).
	 * 종류 → 소속 공간 순으로 잠가 동시에 들어오는 예약 등록과 직렬화한다.
	 */
	public RoomTypeResponse changeActive(long id, boolean active, String reason) {
		FacilityRoomType t = lock(id);
		if (t.isActive() != active) {
			LocalDateTime now = now();
			if (active) {
				t.activate(CurrentUser.idOrNull(), now);
			} else {
				units.lockByRoomTypeId(id);
				long n = bookings.countUnfinishedByRoomType(id, FacilityBookingStatus.CHECKED_IN,
						FacilityBookingStatus.UPCOMING, now);
				if (n > 0)
					throw new ConflictException("이 공간 종류의 공간에 끝나지 않은 예약이 " + n
							+ "건 있어 사용 중지할 수 없습니다. 예약을 먼저 취소하거나 퇴실 처리하세요.");
				t.deactivate(CurrentUser.idOrNull(), now);
			}
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("code", t.getCode());
			String why = FacilityService.blankToNull(reason);
			if (why != null)
				details.put("reason", why);
			audit.log(active ? "ACTIVATE" : "DEACTIVATE", "facility_room_type", id, details);
		}
		return RoomTypeResponse.of(t, units.countByRoomType_Id(id));
	}

	private List<RoomTypeResponse> withUnitCounts(List<FacilityRoomType> types) {
		Map<Long, Long> counts = new HashMap<>();
		List<Long> ids = types.stream().map(FacilityRoomType::getId).toList();
		if (!ids.isEmpty())
			for (Object[] row : units.countByRoomTypeIds(ids))
				counts.put((Long) row[0], (Long) row[1]);
		return types.stream().map(t -> RoomTypeResponse.of(t, counts.getOrDefault(t.getId(), 0L))).toList();
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}

	private FacilityRoomType lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("공간 종류", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
