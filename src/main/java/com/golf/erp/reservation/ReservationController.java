package com.golf.erp.reservation;

import com.golf.erp.common.PageResult;
import com.golf.erp.payment.Payment;
import com.golf.erp.reservation.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
	private final ReservationService service;
	private final ReservationCommandService commandService;

	public ReservationController(ReservationService service, ReservationCommandService commandService) {
		this.service = service;
		this.commandService = commandService;
	}

	/** from/to: 플레이 날짜(양 끝 포함) */
	@GetMapping
	public PageResult<ReservationRow> search(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) ReservationStatus status, @RequestParam(required = false) Long courseId,
			@RequestParam(required = false) Long memberId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.search(keyword, status, courseId, memberId, from, to, page, size);
	}

	@GetMapping("/{id}")
	public ReservationDetail detail(@PathVariable long id) {
		return service.detail(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationDetail create(@Valid @RequestBody ReservationCreateRequest r) {
		return service.detail(commandService.create(r).getReservationId());
	}

	@PostMapping("/{id}/confirm")
	public ReservationDetail confirm(@PathVariable long id) {
		commandService.confirm(id);
		return service.detail(id);
	}

	@PostMapping("/{id}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public Payment pay(@PathVariable long id, @Valid @RequestBody PaymentRequest r) {
		return commandService.pay(id, r.paymentMethod());
	}

	@PostMapping("/{id}/cancel")
	public ReservationDetail cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelRequest r) {
		commandService.cancel(id, r == null ? null : r.reason());
		return service.detail(id);
	}

	@PostMapping("/{id}/complete")
	public ReservationDetail complete(@PathVariable long id) {
		commandService.complete(id);
		return service.detail(id);
	}

	@PostMapping("/{id}/no-show")
	public ReservationDetail noShow(@PathVariable long id) {
		commandService.noShow(id);
		return service.detail(id);
	}

	@PatchMapping("/{id}/memo")
	public ReservationDetail memo(@PathVariable long id, @Valid @RequestBody MemoRequest r) {
		commandService.changeMemo(id, r.memo());
		return service.detail(id);
	}
}
