package com.golf.erp.facility;

import com.golf.erp.facility.FacilityDtos.ActiveRequest;
import com.golf.erp.facility.FacilityDtos.RoomTypeCreateRequest;
import com.golf.erp.facility.FacilityDtos.RoomTypeResponse;
import com.golf.erp.facility.FacilityDtos.RoomTypeUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "부대시설 공간 종류")
@RestController
@RequestMapping("/api")
public class FacilityRoomTypeController {
	private static final String RATE_RULE = "hourlyBaseRate 는 원 단위 정수(소수 불가, 예: 50000)여야 하며 아니면 400. "
			+ "예약 금액 = 시간당 요금 × 이용 시간(15분 단위, 예: 1.25시간). ";

	private final FacilityRoomTypeService roomTypes;

	public FacilityRoomTypeController(FacilityRoomTypeService roomTypes) {
		this.roomTypes = roomTypes;
	}

	@Operation(summary = "공간 종류 목록", description = "facilityId·active·golfCourseId 로 거른다. 시설·종류 코드 순, 페이지 없음. "
			+ "unitCount: 이 종류의 공간 수")
	@GetMapping("/facility-room-types")
	public List<RoomTypeResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long facilityId, @RequestParam(required = false) Boolean active) {
		return roomTypes.list(golfCourseId, facilityId, active);
	}

	@Operation(summary = "시설의 공간 종류 목록", description = "종류 코드 순. 시설이 없으면 404")
	@GetMapping("/facilities/{id}/room-types")
	public List<RoomTypeResponse> ofFacility(@PathVariable long id, @RequestParam(required = false) Boolean active) {
		return roomTypes.listOfFacility(id, active);
	}

	@GetMapping("/facility-room-types/{id}")
	public RoomTypeResponse get(@PathVariable long id) {
		return roomTypes.get(id);
	}

	@Operation(summary = "공간 종류 등록", description = "roomTypeCode 는 시설 안에서 유일(대문자로 저장, 중복 409). "
			+ "defaultCapacity 생략 시 1. " + RATE_RULE + "active 생략 시 사용 중")
	@PostMapping("/facility-room-types")
	@ResponseStatus(HttpStatus.CREATED)
	public RoomTypeResponse create(@Valid @RequestBody RoomTypeCreateRequest r) {
		return roomTypes.create(r);
	}

	@Operation(summary = "공간 종류 수정", description = "코드·이름·기본 수용 인원·시간당 기본요금. " + RATE_RULE
			+ "요금 변경은 새 예약부터 적용되고 이미 받은 예약의 요금 스냅샷은 그대로다. 기존 공간의 수용 인원은 바뀌지 않는다")
	@PutMapping("/facility-room-types/{id}")
	public RoomTypeResponse update(@PathVariable long id, @Valid @RequestBody RoomTypeUpdateRequest r) {
		return roomTypes.update(id, r);
	}

	@Operation(summary = "공간 종류 사용·중지", description = "active=false 는 이 종류의 공간에 끝나지 않은 예약(입실 전이면서 "
			+ "종료 전, 또는 입실 중)이 있으면 409. 사용 중지된 종류로는 새 공간 등록·새 예약을 받지 않는다")
	@PatchMapping("/facility-room-types/{id}/active")
	public RoomTypeResponse changeActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
		return roomTypes.changeActive(id, r.active(), r.reason());
	}
}
