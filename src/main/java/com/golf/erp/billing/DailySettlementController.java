package com.golf.erp.billing;

import com.golf.erp.billing.BillingDtos.SettlementCalculateRequest;
import com.golf.erp.billing.BillingDtos.SettlementCloseRequest;
import com.golf.erp.billing.BillingDtos.SettlementReopenRequest;
import com.golf.erp.billing.BillingDtos.SettlementResponse;
import com.golf.erp.billing.BillingDtos.SettlementSummary;
import com.golf.erp.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "일 마감 정산")
@RestController
@RequestMapping("/api/daily-settlements")
public class DailySettlementController {
	private final DailySettlementService service;

	public DailySettlementController(DailySettlementService service) {
		this.service = service;
	}

	@Operation(summary = "일 마감 목록", description = "from~to: 영업일 범위")
	@GetMapping
	public PageResult<SettlementSummary> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) DailySettlementStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(golfCourseId, from, to, status, page, size);
	}

	@Operation(summary = "일 마감 미리보기", description = "저장하지 않고 지금 기준으로 집계한다")
	@GetMapping("/preview")
	public SettlementResponse preview(@RequestParam long golfCourseId, @RequestParam LocalDate businessDate) {
		return service.preview(golfCourseId, businessDate);
	}

	@Operation(summary = "일 마감 상세", description = "요금 종류별·수납 수단별 항목 포함")
	@GetMapping("/{id}")
	public SettlementResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "일 마감 계산", description = "골프장·영업일의 정산을 만들거나(없으면) 다시 계산한다. 마감(CLOSED) 상태면 409")
	@PostMapping("/calculate")
	public SettlementResponse calculate(@Valid @RequestBody SettlementCalculateRequest r) {
		return service.calculate(r);
	}

	@Operation(summary = "마감", description = "다시 계산한 값을 스냅샷으로 남긴다. 체크인·플레이 중인 라운드가 있으면 409. "
			+ "마감 뒤에는 그날 라운드의 요금·수납을 바꿀 수 없다")
	@PostMapping("/{id}/close")
	public SettlementResponse close(@PathVariable long id, @Valid @RequestBody(required = false) SettlementCloseRequest r) {
		return service.close(id, r == null ? null : r.memo());
	}

	@Operation(summary = "마감 해제", description = "OPEN 으로 돌리고 다시 계산한다")
	@PostMapping("/{id}/reopen")
	public SettlementResponse reopen(@PathVariable long id, @Valid @RequestBody SettlementReopenRequest r) {
		return service.reopen(id, r.reason());
	}
}
