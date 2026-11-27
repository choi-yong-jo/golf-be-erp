package com.golf.erp.facility;

import com.golf.erp.common.PageResult;
import com.golf.erp.facility.FacilityDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "부대시설·공간")
@RestController
@RequestMapping("/api")
public class FacilityController {
	private final FacilityService facilities;
	private final FacilityBookingService bookings;

	public FacilityController(FacilityService facilities, FacilityBookingService bookings) {
		this.facilities = facilities;
		this.bookings = bookings;
	}

	// ---------------------------------------------------------------- 시설

	@Operation(summary = "시설 목록", description = "type: MEETING_ROOM, BANQUET_HALL. keyword: 시설 코드·이름. "
			+ "roomTypeCount·unitCount: 소속 공간 종류·공간 수")
	@GetMapping("/facilities")
	public PageResult<FacilityResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) FacilityType type, @RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return facilities.list(golfCourseId, type, active, keyword, page, size);
	}

	@Operation(summary = "시설 상세", description = "roomTypeCount·unitCount: 소속 공간 종류·공간 수")
	@GetMapping("/facilities/{id}")
	public FacilityResponse get(@PathVariable long id) {
		return facilities.get(id);
	}

	@Operation(summary = "시설 등록", description = "type: MEETING_ROOM, BANQUET_HALL. 시설 코드는 골프장 안에서 유일(중복 409). "
			+ "active 생략 시 운영 중")
	@PostMapping("/facilities")
	@ResponseStatus(HttpStatus.CREATED)
	public FacilityResponse create(@Valid @RequestBody FacilityCreateRequest r) {
		return facilities.create(r);
	}

	@Operation(summary = "시설 수정", description = "코드·이름·유형. 코드 중복 409")
	@PutMapping("/facilities/{id}")
	public FacilityResponse update(@PathVariable long id, @Valid @RequestBody FacilityUpdateRequest r) {
		return facilities.update(id, r);
	}

	@Operation(summary = "시설 운영 재개")
	@PostMapping("/facilities/{id}/activate")
	public FacilityResponse activate(@PathVariable long id) {
		return facilities.activate(id);
	}

	@Operation(summary = "시설 비활성화", description = "소속 공간에 끝나지 않은 예약(입실 전이면서 종료 전, 또는 입실 중)이 있으면 409. "
			+ "비활성 시설의 공간은 새 예약을 받지 않는다")
	@PostMapping("/facilities/{id}/deactivate")
	public FacilityResponse deactivate(@PathVariable long id, @Valid @RequestBody(required = false) ReasonRequest r) {
		return facilities.deactivate(id, r == null ? null : r.reason());
	}

	@Operation(summary = "시설의 공간 목록", description = "공간 코드 순, 페이지 없음")
	@GetMapping("/facilities/{id}/units")
	public List<UnitResponse> unitsOf(@PathVariable long id) {
		return facilities.unitsOf(id);
	}

	// ---------------------------------------------------------------- 공간

	@Operation(summary = "공간 목록", description = "type: 시설 유형. status: AVAILABLE, MAINTENANCE, CLOSED. "
			+ "facilityRoomTypeId: 공간 종류. keyword: 공간 코드·이름")
	@GetMapping("/facility-units")
	public PageResult<UnitResponse> units(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long facilityId, @RequestParam(required = false) Long facilityRoomTypeId,
			@RequestParam(required = false) FacilityUnitStatus status, @RequestParam(required = false) FacilityType type,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return facilities.listUnits(golfCourseId, facilityId, facilityRoomTypeId, status, type, keyword, page, size);
	}

	@Operation(summary = "예약 가능 공간 조회", description = "from/to 는 15분 단위(분 0·15·30·45, 초 0, 예: 2026-11-26T14:15:00), "
			+ "아니면 400. 최대 " + FacilityPricing.MAX_BOOKING_DAYS + "일. 시설 활성·공간 종류 사용 중·공간 AVAILABLE, "
			+ "수용 인원 ≥ guestCount(기본 1)이고 [from, to) 와 겹치는 PENDING·CONFIRMED·CHECKED_IN 예약이 없는 공간. "
			+ "hourlyRate: 공간 종류의 시간당 기본요금(원 단위 정수), bookingHours: 이용 시간(예: 1.25), "
			+ "estimatedBaseAmount = hourlyRate × bookingHours")
	@GetMapping("/facility-units/availability")
	public List<AvailableUnitResponse> availability(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) FacilityType facilityType, @RequestParam(required = false) Long facilityId,
			@RequestParam(required = false) Long facilityRoomTypeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(required = false) Integer guestCount) {
		return bookings.availability(golfCourseId, facilityType, facilityId, facilityRoomTypeId, from, to, guestCount);
	}

	@GetMapping("/facility-units/{id}")
	public UnitResponse unit(@PathVariable long id) {
		return facilities.getUnit(id);
	}

	@Operation(summary = "공간 등록", description = "facilityRoomTypeId 는 같은 시설의 공간 종류(다른 시설 400, 없음 404, "
			+ "사용 중지 409). 공간 코드는 시설 안에서 유일(중복 409). capacity 생략 시 공간 종류의 기본 수용 인원. "
			+ "요금은 공간 종류의 시간당 기본요금을 쓴다")
	@PostMapping("/facility-units")
	@ResponseStatus(HttpStatus.CREATED)
	public UnitResponse createUnit(@Valid @RequestBody UnitCreateRequest r) {
		return facilities.createUnit(r);
	}

	@Operation(summary = "공간 수정", description = "시설은 바꿀 수 없고 공간 종류는 같은 시설의 것으로만 바꿀 수 있다(400). "
			+ "사용 중지된 종류로는 바꿀 수 없다(409). 이미 받은 예약은 요금 스냅샷을 유지한다. "
			+ "capacity 생략 시 공간 종류의 기본 수용 인원. 끝나지 않은 예약 인원보다 수용 인원을 줄이면 409")
	@PutMapping("/facility-units/{id}")
	public UnitResponse updateUnit(@PathVariable long id, @Valid @RequestBody UnitUpdateRequest r) {
		return facilities.updateUnit(id, r);
	}

	@Operation(summary = "공간 운영 상태 변경", description = "AVAILABLE, MAINTENANCE, CLOSED. 입실 중(CHECKED_IN) 예약이 있으면 "
			+ "MAINTENANCE·CLOSED 로 바꿀 수 없다(409). 입실 전 예약은 취소되지 않고 upcomingBookingCount·warning 으로 알려 준다")
	@PatchMapping("/facility-units/{id}/status")
	public UnitStatusResponse changeUnitStatus(@PathVariable long id, @Valid @RequestBody UnitStatusRequest r) {
		return facilities.changeUnitStatus(id, r.status(), r.reason());
	}
}
