package com.golf.erp.settlement;

import com.golf.erp.settlement.SettlementDtos.SettlementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Tag(name = "정산")
@RestController
@RequestMapping("/api/settlements")
public class SettlementController {
	private final SettlementService service;

	public SettlementController(SettlementService service) {
		this.service = service;
	}

	@Operation(summary = "일별 정산 목록", description = "month=yyyy-MM 또는 from/to(양 끝 포함, 최대 1년). 생략 시 이번 달")
	@GetMapping
	public List<SettlementResponse> list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return service.list(month, from, to);
	}

	@GetMapping("/{date}")
	public SettlementResponse get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.get(date);
	}

	@Operation(summary = "정산 생성·재계산", description = "예약 결제 기준으로 그날 금액을 다시 집계. 마감된 날짜는 409")
	@PostMapping("/{date}/calculate")
	public SettlementResponse calculate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.calculate(date);
	}

	@Operation(summary = "정산 마감", description = "재계산 후 마감. 지난 날짜만 가능")
	@PostMapping("/{date}/close")
	public SettlementResponse close(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.close(date);
	}

	@Operation(summary = "정산 마감 해제")
	@PostMapping("/{date}/reopen")
	public SettlementResponse reopen(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return service.reopen(date);
	}
}
