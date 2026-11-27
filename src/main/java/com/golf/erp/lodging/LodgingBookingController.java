package com.golf.erp.lodging;

import com.golf.erp.common.PageResult;
import com.golf.erp.lodging.LodgingDtos.BookingCreateRequest;
import com.golf.erp.lodging.LodgingDtos.BookingCreateResponse;
import com.golf.erp.lodging.LodgingDtos.BookingResponse;
import com.golf.erp.lodging.LodgingDtos.BookingUpdateRequest;
import com.golf.erp.lodging.LodgingDtos.CalendarResponse;
import com.golf.erp.lodging.LodgingDtos.CancelRequest;
import com.golf.erp.lodging.LodgingDtos.PaymentStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "숙박 예약")
@RestController
@RequestMapping("/api/lodging-bookings")
public class LodgingBookingController {
	private final LodgingBookingService bookings;

	public LodgingBookingController(LodgingBookingService bookings) {
		this.bookings = bookings;
	}

	@Operation(summary = "숙박 예약 목록", description = "from/to: 예약 기간이 이 날짜 범위와 겹치는 것. "
			+ "keyword: 손님 이름·전화번호·예약 번호. 입실 예정 최근 순")
	@GetMapping
	public PageResult<BookingResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long roomTypeId, @RequestParam(required = false) Long roomId,
			@RequestParam(required = false) LodgingBookingType bookingType,
			@RequestParam(required = false) LodgingBookingStatus status,
			@RequestParam(required = false) LodgingPaymentStatus paymentStatus,
			@RequestParam(required = false) Long memberId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bookings.list(golfCourseId, roomTypeId, roomId, bookingType, status, paymentStatus, memberId, from, to,
				keyword, page, size);
	}

	@Operation(summary = "숙박 예약 달력", description = "객실별 예약(취소·노쇼 제외). golfCourseId 또는 roomTypeId 필수, "
			+ "from~to(포함) 최대 62일")
	@GetMapping("/calendar")
	public CalendarResponse calendar(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long roomTypeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return bookings.calendar(golfCourseId, roomTypeId, from, to);
	}

	@GetMapping("/{id}")
	public BookingResponse get(@PathVariable long id) {
		return bookings.get(id);
	}

	@Operation(summary = "숙박 예약 등록", description = "DAY_USE: date checkInTime 입실 → + dayUseHours(생략 시 객실 타입 값), "
			+ "요금 = 대실 요금. OVERNIGHT: date 16:00 → 다음 날 11:00, 요금 = 1박 요금. "
			+ "nights(1~14)를 주면 박마다 예약 한 건씩 연속으로 만든다(DDL 상 한 건 = 1박). discountAmount 는 건마다 적용. "
			+ "객실이 MAINTENANCE·CLOSED 이거나 타입이 비활성이면 409, 같은 객실의 PENDING·CONFIRMED·CHECKED_IN 예약과 "
			+ "겹치면 409. guestCount 는 정원 확인에만 쓰고 저장하지 않는다. memberId 가 있으면 비어 있는 이름·전화는 회원 정보로 채운다. "
			+ "tentative=true 면 PENDING")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookingCreateResponse create(@Valid @RequestBody BookingCreateRequest r) {
		return bookings.create(r);
	}

	@Operation(summary = "숙박 예약 수정", description = "입실 전(PENDING·CONFIRMED)만. 날짜·입실 시각·대실 시간·손님·할인·요청 사항. "
			+ "객실·유형·요금은 바꾸지 않는다(취소 후 재예약)")
	@PutMapping("/{id}")
	public BookingResponse update(@PathVariable long id, @Valid @RequestBody BookingUpdateRequest r) {
		return bookings.update(id, r);
	}

	@Operation(summary = "예약 확정", description = "PENDING → CONFIRMED")
	@PostMapping("/{id}/confirm")
	public BookingResponse confirm(@PathVariable long id) {
		return bookings.confirm(id);
	}

	@Operation(summary = "입실", description = "CONFIRMED → CHECKED_IN. 예약일부터 퇴실 예정 시각 전까지. "
			+ "객실이 청소 중·보수·운영 중지이거나 이전 손님이 퇴실하지 않았으면 409")
	@PostMapping("/{id}/check-in")
	public BookingResponse checkIn(@PathVariable long id) {
		return bookings.checkIn(id);
	}

	@Operation(summary = "퇴실", description = "CHECKED_IN → CHECKED_OUT. 객실은 CLEANING 으로 바뀐다")
	@PostMapping("/{id}/check-out")
	public BookingResponse checkOut(@PathVariable long id) {
		return bookings.checkOut(id);
	}

	@Operation(summary = "예약 취소", description = "PENDING·CONFIRMED 만. 환불은 수납 상태(REFUNDED)로 따로 기록")
	@PostMapping("/{id}/cancel")
	public BookingResponse cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelRequest r) {
		return bookings.cancel(id, r == null ? null : r.reason());
	}

	@Operation(summary = "노쇼", description = "CONFIRMED 이고 입실 예정 시각이 지난 예약만")
	@PostMapping("/{id}/no-show")
	public BookingResponse noShow(@PathVariable long id) {
		return bookings.noShow(id);
	}

	@Operation(summary = "수납 상태 변경", description = "UNPAID, PARTIAL, PAID, REFUNDED. REFUNDED 는 PARTIAL·PAID 에서만")
	@PatchMapping("/{id}/payment-status")
	public BookingResponse changePaymentStatus(@PathVariable long id, @Valid @RequestBody PaymentStatusRequest r) {
		return bookings.changePaymentStatus(id, r.paymentStatus(), r.memo());
	}
}
