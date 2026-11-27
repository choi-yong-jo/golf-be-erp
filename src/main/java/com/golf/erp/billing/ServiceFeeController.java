package com.golf.erp.billing;

import com.golf.erp.billing.BillingDtos.ServiceFeeCreateRequest;
import com.golf.erp.billing.BillingDtos.ServiceFeeResponse;
import com.golf.erp.billing.BillingDtos.ServiceFeeUpdateRequest;
import com.golf.erp.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "부대 요금표")
@RestController
@RequestMapping("/api/service-fees")
public class ServiceFeeController {
	private final ServiceFeeService service;

	public ServiceFeeController(ServiceFeeService service) {
		this.service = service;
	}

	@Operation(summary = "부대 요금 목록", description = "date: 그날 적용 기간에 걸친 요금만. active: 사용 여부")
	@GetMapping
	public PageResult<ServiceFeeResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) FeeType feeType, @RequestParam(required = false) LocalDate date,
			@RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(golfCourseId, feeType, date, active, page, size);
	}

	@GetMapping("/{id}")
	public ServiceFeeResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "부대 요금 등록", description = "같은 골프장·종류·이름·홀 기준의 사용 중인 요금과 기간이 겹치면 409")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ServiceFeeResponse create(@Valid @RequestBody ServiceFeeCreateRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public ServiceFeeResponse update(@PathVariable long id, @Valid @RequestBody ServiceFeeUpdateRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "부대 요금 사용 중지", description = "물리 삭제하지 않고 is_active=false 로 바꾼다")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}
}
