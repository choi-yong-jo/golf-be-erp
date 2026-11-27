package com.golf.erp.payment;

import com.golf.erp.common.PageResult;
import com.golf.erp.payment.PaymentDtos.PaymentDetail;
import com.golf.erp.payment.PaymentDtos.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 예약 결제 내역 조회. 결제 등록·환불은 예약 API(/api/reservations/{id}/payments, /cancel)에서 한다. */
@Tag(name = "예약 결제")
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
	private final PaymentService service;

	public PaymentController(PaymentService service) {
		this.service = service;
	}

	@Operation(summary = "결제 내역 목록", description = "예약 앱 온라인 결제 포함. from/to: 결제 요청일(양 끝 포함). pgProvider=ERP 면 현장 결제만")
	@GetMapping
	public PageResult<PaymentResponse> list(@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) PaymentType type, @RequestParam(required = false) String method,
			@RequestParam(required = false) String pgProvider, @RequestParam(required = false) Long reservationId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, type, method, pgProvider, reservationId, from, to, page, size);
	}

	@GetMapping("/{id}")
	public PaymentDetail get(@PathVariable long id) {
		return service.get(id);
	}
}
