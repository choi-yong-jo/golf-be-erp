package com.golf.erp.lodging;

import com.golf.erp.lodging.LodgingDtos.RoomTypeCreateRequest;
import com.golf.erp.lodging.LodgingDtos.RoomTypeDetailResponse;
import com.golf.erp.lodging.LodgingDtos.RoomTypeResponse;
import com.golf.erp.lodging.LodgingDtos.RoomTypeUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "숙박 객실 타입")
@RestController
@RequestMapping("/api/lodging-room-types")
public class LodgingRoomTypeController {
	private final LodgingRoomTypeService types;

	public LodgingRoomTypeController(LodgingRoomTypeService types) {
		this.types = types;
	}

	@Operation(summary = "객실 타입 목록", description = "keyword: 타입 코드·이름. 전체 목록(페이지 없음)")
	@GetMapping
	public List<RoomTypeResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Boolean active, @RequestParam(required = false) String keyword) {
		return types.list(golfCourseId, active, keyword);
	}

	@Operation(summary = "객실 타입 상세", description = "상태별 객실 수 포함")
	@GetMapping("/{id}")
	public RoomTypeDetailResponse get(@PathVariable long id) {
		return types.get(id);
	}

	@Operation(summary = "객실 타입 등록", description = "roomTypeCode 는 골프장 안에서 유일, 대문자로 저장. "
			+ "생략 시 정원 2명, 대실 3시간, 요금 0. 요금은 원 단위")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoomTypeDetailResponse create(@Valid @RequestBody RoomTypeCreateRequest r) {
		return types.create(r);
	}

	@Operation(summary = "객실 타입 수정", description = "이름·정원·대실 시간·요금. 이미 받은 예약의 시간·금액은 바뀌지 않는다")
	@PutMapping("/{id}")
	public RoomTypeDetailResponse update(@PathVariable long id, @Valid @RequestBody RoomTypeUpdateRequest r) {
		return types.update(id, r);
	}

	@PostMapping("/{id}/activate")
	public RoomTypeDetailResponse activate(@PathVariable long id) {
		return types.activate(id);
	}

	@Operation(summary = "객실 타입 비활성화", description = "이 타입 객실에 입실 중이거나 예정된 예약이 있으면 409. "
			+ "비활성 타입의 객실은 새 예약을 받지 않는다")
	@PostMapping("/{id}/deactivate")
	public RoomTypeDetailResponse deactivate(@PathVariable long id) {
		return types.deactivate(id);
	}
}
