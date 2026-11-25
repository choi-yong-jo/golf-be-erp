package com.golf.erp.settlement;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {
	private final SettlementService service;

	public SettlementController(SettlementService service) {
		this.service = service;
	}

	@GetMapping
	public List<Settlement> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.list(from, to);
	}

	@GetMapping("/{date}")
	public Settlement get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.get(date);
	}

	/** 해당 일자 정산 생성 또는 재계산 */
	@PostMapping("/{date}/calculate")
	public Settlement calculate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.calculate(date);
	}

	/** 재계산 후 마감 */
	@PostMapping("/{date}/close")
	public Settlement close(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.close(date);
	}

	@PostMapping("/{date}/reopen")
	public Settlement reopen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.reopen(date);
	}
}
