package com.golf.erp.facility;

import com.golf.erp.common.PageResult;
import com.golf.erp.facility.FacilityDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "부대시설 예약")
@RestController
@RequestMapping("/api/facility-bookings")
public class FacilityBookingController {
	private static final String TIME_RULE = "startAt/endAt 은 15분 단위(분 0·15·30·45, 초 0, 예: 2026-11-26T14:15:00)여야 하며 "
			+ "아니면 400. 최대 " + FacilityPricing.MAX_BOOKING_DAYS + "일. ";
	private static final String AMOUNT_RULE = "금액은 서버가 계산한다: bookingHours = 이용 분 / 60(예: 1시간 15분 → 1.25), "
			+ "hourlyRate = 예약 등록 시점 공간 종류의 시간당 기본요금 스냅샷(원 단위 정수), baseAmount = hourlyRate × bookingHours "
			+ "(반올림 없음), discountAmount(생략 시 0, 소수 둘째 자리까지) ≤ baseAmount, totalAmount = baseAmount − discountAmount. "
			+ "요금 조정은 discountAmount 로만 한다. ";

	private final FacilityBookingService bookings;

	public FacilityBookingController(FacilityBookingService bookings) {
		this.bookings = bookings;
	}

	@Operation(summary = "시설 예약 목록", description = "from/to: 예약 기간이 이 날짜 범위와 겹치는 것. "
			+ "facilityRoomTypeId: 공간의 현재 종류. keyword: 손님 이름·전화번호·예약 번호. 최근 시작 순")
	@GetMapping
	public PageResult<BookingResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long facilityId, @RequestParam(required = false) Long facilityRoomTypeId,
			@RequestParam(required = false) Long unitId, @RequestParam(required = false) FacilityBookingStatus status,
			@RequestParam(required = false) FacilityPaymentStatus paymentStatus,
			@RequestParam(required = false) Long memberId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return bookings.list(golfCourseId, facilityId, facilityRoomTypeId, unitId, status, paymentStatus, memberId,
				from, to, keyword, page, size);
	}

	@Operation(summary = "시설 예약 달력", description = "시설 한 곳의 공간별 점유 예약(PENDING·CONFIRMED·CHECKED_IN). "
			+ "from~to(포함) 최대 62일")
	@GetMapping("/calendar")
	public CalendarResponse calendar(@RequestParam long facilityId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return bookings.calendar(facilityId, from, to);
	}

	@GetMapping("/{id}")
	public BookingResponse get(@PathVariable long id) {
		return bookings.get(id);
	}

	@Operation(summary = "시설 예약 등록", description = TIME_RULE + "기간은 [startAt, endAt). 시설 활성·공간 종류 사용 중·"
			+ "공간 AVAILABLE 이어야 하며(아니면 409), guestCount ≤ 수용 인원, 같은 공간의 PENDING·CONFIRMED·CHECKED_IN 예약과 "
			+ "겹치면 409. memberId 가 있으면 guestName·guestPhone 생략 시 회원 정보 사용. tentative=true 면 PENDING(가예약), "
			+ "아니면 CONFIRMED. " + AMOUNT_RULE + "번호: FB-yyyyMMdd-000123")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookingResponse create(@Valid @RequestBody BookingCreateRequest r) {
		return bookings.create(r);
	}

	@Operation(summary = "시설 예약 수정", description = "PENDING·CONFIRMED 만. " + TIME_RULE
			+ "기간이 바뀌면 시설·공간 종류·공간 상태와 겹침을 다시 확인한다. " + AMOUNT_RULE
			+ "수정 시 hourlyRate 는 등록 때 스냅샷을 유지하고 bookingHours·baseAmount·totalAmount 만 다시 계산한다")
	@PutMapping("/{id}")
	public BookingResponse update(@PathVariable long id, @Valid @RequestBody BookingUpdateRequest r) {
		return bookings.update(id, r);
	}

	@Operation(summary = "예약 확정", description = "PENDING → CONFIRMED")
	@PostMapping("/{id}/confirm")
	public BookingResponse confirm(@PathVariable long id) {
		return bookings.confirm(id);
	}

	@Operation(summary = "입실(체크인)", description = "CONFIRMED → CHECKED_IN. 예약 시작일 전이나 예약 기간이 끝난 뒤에는 409")
	@PostMapping("/{id}/check-in")
	public BookingResponse checkIn(@PathVariable long id) {
		return bookings.checkIn(id);
	}

	@Operation(summary = "퇴실(체크아웃)", description = "CHECKED_IN → CHECKED_OUT")
	@PostMapping("/{id}/check-out")
	public BookingResponse checkOut(@PathVariable long id) {
		return bookings.checkOut(id);
	}

	@Operation(summary = "예약 취소", description = "PENDING·CONFIRMED 만. 수납 상태는 바뀌지 않으므로 환불했으면 수납 상태를 REFUNDED 로 기록")
	@PostMapping("/{id}/cancel")
	public BookingResponse cancel(@PathVariable long id, @Valid @RequestBody(required = false) ReasonRequest r) {
		return bookings.cancel(id, r == null ? null : r.reason());
	}

	@Operation(summary = "노쇼 처리", description = "CONFIRMED 이고 예약 시작 시각이 지난 경우만")
	@PostMapping("/{id}/no-show")
	public BookingResponse noShow(@PathVariable long id) {
		return bookings.noShow(id);
	}

	@Operation(summary = "수납 상태 변경", description = "UNPAID, PARTIAL, PAID, REFUNDED. 결제 테이블이 없어 직원이 수납 결과를 기록한다. "
			+ "REFUNDED 는 PARTIAL·PAID 에서만")
	@PatchMapping("/{id}/payment-status")
	public BookingResponse changePaymentStatus(@PathVariable long id, @Valid @RequestBody PaymentStatusRequest r) {
		return bookings.changePaymentStatus(id, r.paymentStatus(), r.memo());
	}
}
