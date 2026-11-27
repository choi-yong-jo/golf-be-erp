package com.golf.erp.fee;

import com.golf.erp.fee.FeeDtos.FeeQuote;
import com.golf.erp.fee.FeeDtos.GreenFeePolicyRequest;
import com.golf.erp.fee.FeeDtos.GreenFeePolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Tag(name = "그린피 정책")
@RestController
@RequestMapping("/api/fee-policies")
public class FeeController {
	private final GreenFeePolicyService service;

	public FeeController(GreenFeePolicyService service) {
		this.service = service;
	}

	@Operation(summary = "그린피 정책 목록", description = "golfCourseId 지정 시 해당 골프장 정책 + 공통 정책. "
			+ "onDate: 그 날짜에 유효한 정책만. 건수가 적어 페이지 없음")
	@GetMapping
	public List<GreenFeePolicyResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(defaultValue = "false") boolean activeOnly,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
		return service.list(golfCourseId, activeOnly, onDate);
	}

	@Operation(summary = "그린피 조회", description = "골프장·날짜·시각에 적용되는 1인 요금. 우선순위: 특정 골프장 > 공통, "
			+ "요일 지정 > 미지정, 평일/주말 지정 > 미지정, 시간대 지정 > 미지정, 최근 시작일 > 최근 등록. 정책이 없으면 policyId=null")
	@GetMapping("/quote")
	public FeeQuote quote(@RequestParam long golfCourseId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
		return service.quote(golfCourseId, date, time);
	}

	@GetMapping("/{id}")
	public GreenFeePolicyResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GreenFeePolicyResponse create(@Valid @RequestBody GreenFeePolicyRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public GreenFeePolicyResponse update(@PathVariable long id, @Valid @RequestBody GreenFeePolicyRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "그린피 정책 비활성", description = "물리 삭제 대신 is_active=false")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable long id) {
		service.deactivate(id);
	}
}
