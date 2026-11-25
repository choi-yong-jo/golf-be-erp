package com.golf.erp.payment;

import com.golf.erp.common.PageResult;
import com.golf.erp.payment.dto.PaymentRow;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 결제 내역 조회. 결제 등록/환불은 예약 API(/api/reservations/{id}/payments, /cancel)에서 한다. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
	private final PaymentMapper mapper;

	public PaymentController(PaymentMapper mapper) {
		this.mapper = mapper;
	}

	/** from/to: 결제 요청일(양 끝 포함) */
	@GetMapping
	public PageResult<PaymentRow> search(@RequestParam(required = false) PaymentStatus status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		PageResult.validate(page, size);
		String st = status == null ? null : status.name();
		LocalDateTime f = from == null ? null : from.atStartOfDay();
		LocalDateTime t = to == null ? null : to.plusDays(1).atStartOfDay();
		return new PageResult<>(mapper.search(st, f, t, page * size, size), page, size, mapper.count(st, f, t));
	}
}
