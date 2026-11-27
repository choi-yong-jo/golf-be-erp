package com.golf.erp.crm;

import com.golf.erp.common.PageResult;
import com.golf.erp.crm.CrmDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "프로모션")
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {
	private final PromotionService service;

	public PromotionController(PromotionService service) {
		this.service = service;
	}

	@Operation(summary = "프로모션 목록", description = "keyword: 코드·이름, current=true: 지금 사용 가능(활성 + 기간 안)")
	@GetMapping
	public PageResult<PromotionResponse> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Boolean current, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(active, current, keyword, page, size);
	}

	@Operation(summary = "코드 검증·할인액 계산", description = "AMOUNT: min(할인값, amount), PERCENT: amount × 할인율 원 단위 절사."
			+ " 쓸 수 없는 코드면 valid=false 와 사유(200)")
	@GetMapping("/validate")
	public PromotionQuote validate(@RequestParam String code, @RequestParam BigDecimal amount) {
		return service.validate(code, amount);
	}

	@GetMapping("/{id}")
	public PromotionResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "프로모션 등록", description = "코드는 대문자로 저장. PERCENT 는 100 이하, 기간 [startsAt, endsAt)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PromotionResponse create(@Valid @RequestBody PromotionRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public PromotionResponse update(@PathVariable long id, @Valid @RequestBody PromotionRequest r) {
		return service.update(id, r);
	}

	@PatchMapping("/{id}/active")
	public PromotionResponse changeActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
		return service.changeActive(id, r.active());
	}

	@Operation(summary = "프로모션 비활성화", description = "물리 삭제하지 않고 is_active=false 로 바꾼다")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable long id) {
		service.changeActive(id, false);
	}

	@Operation(summary = "프로모션 사용 등록", description = "회원 필수, 예약 선택. 활성·기간 안이어야 하고 같은 예약에는 한 번만."
			+ " 할인액은 baseAmount 로 서버가 계산")
	@PostMapping("/{id}/redemptions")
	@ResponseStatus(HttpStatus.CREATED)
	public RedemptionResponse redeem(@PathVariable long id, @Valid @RequestBody RedemptionRequest r) {
		return service.redeem(id, r);
	}

	@Operation(summary = "프로모션 사용 이력")
	@GetMapping("/{id}/redemptions")
	public PageResult<RedemptionResponse> redemptions(@PathVariable long id,
			@RequestParam(required = false) Long memberId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.redemptions(id, memberId, page, size);
	}
}
