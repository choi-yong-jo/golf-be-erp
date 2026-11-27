package com.golf.erp.lodging;

import com.golf.erp.common.PageResult;
import com.golf.erp.lodging.LodgingDtos.AvailableRoomResponse;
import com.golf.erp.lodging.LodgingDtos.RoomCreateRequest;
import com.golf.erp.lodging.LodgingDtos.RoomDetailResponse;
import com.golf.erp.lodging.LodgingDtos.RoomResponse;
import com.golf.erp.lodging.LodgingDtos.RoomStatusRequest;
import com.golf.erp.lodging.LodgingDtos.RoomUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Tag(name = "숙박 객실")
@RestController
@RequestMapping("/api/lodging-rooms")
public class LodgingRoomController {
	private final LodgingRoomService rooms;

	public LodgingRoomController(LodgingRoomService rooms) {
		this.rooms = rooms;
	}

	@Operation(summary = "객실 목록", description = "keyword: 객실 번호·이름. 타입 코드·객실 번호 순")
	@GetMapping
	public PageResult<RoomResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) LodgingRoomStatus status,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return rooms.list(golfCourseId, roomTypeId, status, keyword, page, size);
	}

	@Operation(summary = "예약 가능 객실", description = "OVERNIGHT: date 16:00 입실 → 다음 날 11:00 퇴실(nights 박 모두 비어 있어야 함). "
			+ "DAY_USE: date checkInTime 입실 → + dayUseHours(생략 시 객실 타입의 대실 시간). "
			+ "타입이 활성이고 객실이 AVAILABLE·CLEANING 이며 정원 ≥ guestCount, 겹치는 예약(PENDING·CONFIRMED·CHECKED_IN)이 "
			+ "없는 객실과 할인 전 금액(요금 × 박 수). golfCourseId 또는 roomTypeId 필수")
	@GetMapping("/availability")
	public List<AvailableRoomResponse> availability(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long roomTypeId, @RequestParam LodgingBookingType bookingType,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime checkInTime,
			@RequestParam(required = false) Short dayUseHours, @RequestParam(required = false) Integer nights,
			@RequestParam(required = false) Integer guestCount) {
		return rooms.availability(golfCourseId, roomTypeId, bookingType, date, checkInTime, dayUseHours, nights,
				guestCount);
	}

	@Operation(summary = "객실 상세", description = "입실 중인 예약 번호, 입실 전 예약 수 포함")
	@GetMapping("/{id}")
	public RoomDetailResponse get(@PathVariable long id) {
		return rooms.get(id);
	}

	@Operation(summary = "객실 등록", description = "roomCode 는 객실 타입 안에서 유일. 처음 상태는 AVAILABLE")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RoomDetailResponse create(@Valid @RequestBody RoomCreateRequest r) {
		return rooms.create(r);
	}

	@Operation(summary = "객실 수정", description = "객실 타입은 같은 골프장의 타입으로만, 이 객실에 입실 중이거나 예정된 예약이 "
			+ "없을 때만 바꿀 수 있다(409)")
	@PutMapping("/{id}")
	public RoomDetailResponse update(@PathVariable long id, @Valid @RequestBody RoomUpdateRequest r) {
		return rooms.update(id, r);
	}

	@Operation(summary = "객실 상태 변경", description = "AVAILABLE, CLEANING, MAINTENANCE, CLOSED. "
			+ "MAINTENANCE·CLOSED 는 입실 중인 예약이 있으면 409. 응답의 upcomingBookingCount 로 남은 예약 수를 알려 준다")
	@PatchMapping("/{id}/status")
	public RoomDetailResponse changeStatus(@PathVariable long id, @Valid @RequestBody RoomStatusRequest r) {
		return rooms.changeStatus(id, r.status());
	}

	@Operation(summary = "청소 완료", description = "CLEANING → AVAILABLE. 퇴실 처리 시 객실은 자동으로 CLEANING 이 된다")
	@PostMapping("/{id}/cleaned")
	public RoomDetailResponse cleaned(@PathVariable long id) {
		return rooms.cleaned(id);
	}
}
