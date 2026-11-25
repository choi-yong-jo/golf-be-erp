package com.golf.erp.fee;

import com.golf.erp.fee.dto.FeeQuote;
import com.golf.erp.fee.dto.GreenFeePolicyRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/fee-policies")
public class GreenFeePolicyController {
	private final GreenFeePolicyService service;

	public GreenFeePolicyController(GreenFeePolicyService service) {
		this.service = service;
	}

	/** courseId 지정 시 해당 코스 정책 + 공통 정책 */
	@GetMapping
	public List<GreenFeePolicy> search(@RequestParam(required = false) Long courseId,
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return service.search(courseId, activeOnly);
	}

	/** 코스·날짜·시각에 적용되는 1인 요금 */
	@GetMapping("/quote")
	public FeeQuote quote(@RequestParam long courseId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
		return service.quote(courseId, date, time);
	}

	@GetMapping("/{id}")
	public GreenFeePolicy get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GreenFeePolicy create(@Valid @RequestBody GreenFeePolicyRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public GreenFeePolicy update(@PathVariable long id, @Valid @RequestBody GreenFeePolicyRequest r) {
		return service.update(id, r);
	}

	/** 비활성 처리 (is_active=false) */
	@DeleteMapping("/{id}")
	public GreenFeePolicy deactivate(@PathVariable long id) {
		return service.deactivate(id);
	}
}
