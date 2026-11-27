package com.golf.erp.reservation;

import com.golf.erp.common.PageResult;
import com.golf.erp.reservation.ReservationDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "예약")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
	private final ReservationQueryService query;
	private final ReservationService service;

	public ReservationController(ReservationQueryService query, ReservationService service) {
		this.query = query;
		this.service = service;
	}

	@Operation(summary = "예약 목록", description = "예약 앱 예약 포함. keyword: 예약번호·회원 이름·전화. from/to: 플레이 날짜(양 끝 포함)")
	@GetMapping
	public PageResult<ReservationRow> list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) ReservationStatus status, @RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Long memberId, @RequestParam(required = false) Long teeTimeId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return query.list(keyword, status, golfCourseId, memberId, teeTimeId, from, to, page, size);
	}

	@Operation(summary = "예약 상세", description = "플레이어, 결제, 변경 이력, 체크인한 라운드 포함")
	@GetMapping("/{id}")
	public ReservationDetail detail(@PathVariable long id) {
		return query.detail(id);
	}

	@Operation(summary = "예약 등록(ERP)", description = "PENDING 으로 생성, 티타임은 BOOKED. 예약자는 1번 플레이어로 자동 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationDetail create(@Valid @RequestBody ReservationCreateRequest r) {
		return service.create(r);
	}

	@Operation(summary = "예약 확정", description = "PENDING → CONFIRMED (결제 없이 확정)")
	@PostMapping("/{id}/confirm")
	public ReservationDetail confirm(@PathVariable long id) {
		return service.confirm(id);
	}

	@Operation(summary = "현장 결제", description = "예약 총액을 PAID 로 기록(pg_provider=ERP). PENDING 이면 함께 확정")
	@PostMapping("/{id}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationDetail pay(@PathVariable long id, @Valid @RequestBody PaymentRequest r) {
		return service.pay(id, r.paymentMethod());
	}

	@Operation(summary = "예약 취소", description = "ERP 결제는 REFUNDED 로 환불, 티타임은 OPEN. 온라인 결제·PAYING·REFUND_PENDING 은 예약 앱에서 처리(409)")
	@PostMapping("/{id}/cancel")
	public ReservationDetail cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelRequest r) {
		return service.cancel(id, r == null ? null : r.reason());
	}

	@Operation(summary = "플레이 완료", description = "CONFIRMED → COMPLETED (플레이 시작 시각 이후)")
	@PostMapping("/{id}/complete")
	public ReservationDetail complete(@PathVariable long id) {
		return service.complete(id);
	}

	@Operation(summary = "노쇼", description = "CONFIRMED → NO_SHOW (플레이 시작 시각 이후, 체크인하지 않은 예약)")
	@PostMapping("/{id}/no-show")
	public ReservationDetail noShow(@PathVariable long id) {
		return service.noShow(id);
	}

	@PatchMapping("/{id}/memo")
	public ReservationDetail memo(@PathVariable long id, @Valid @RequestBody MemoRequest r) {
		return service.changeMemo(id, r.memo());
	}
}
