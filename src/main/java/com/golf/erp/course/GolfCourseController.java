package com.golf.erp.course;

import com.golf.erp.course.CourseDtos.ClosureRequest;
import com.golf.erp.course.CourseDtos.ClosureResponse;
import com.golf.erp.course.CourseDtos.GolfCourseRequest;
import com.golf.erp.course.CourseDtos.GolfCourseResponse;
import com.golf.erp.course.CourseDtos.GolfCourseStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "골프장·휴장")
@RestController
@RequestMapping("/api/golf-courses")
public class GolfCourseController {
	private final GolfCourseService service;

	public GolfCourseController(GolfCourseService service) {
		this.service = service;
	}

	@Operation(summary = "골프장 목록", description = "건수가 적어 페이지 없이 전체를 돌려준다. keyword: 코드·이름")
	@GetMapping
	public List<GolfCourseResponse> list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) GolfCourseStatus status, @RequestParam(required = false) String keyword) {
		return service.list(active, status, keyword);
	}

	@GetMapping("/{id}")
	public GolfCourseResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GolfCourseResponse create(@Valid @RequestBody GolfCourseRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public GolfCourseResponse update(@PathVariable long id, @Valid @RequestBody GolfCourseRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "골프장 운영 상태 변경", description = "status: OPEN/CLOSED, active=false 면 비활성(미사용)")
	@PatchMapping("/{id}/status")
	public GolfCourseResponse changeStatus(@PathVariable long id, @Valid @RequestBody GolfCourseStatusRequest r) {
		return service.changeStatus(id, r);
	}

	@Operation(summary = "휴장 목록", description = "activeOnly=true 면 해제되지 않은 휴장만. 시작 시각 내림차순")
	@GetMapping("/{id}/closures")
	public List<ClosureResponse> closures(@PathVariable long id,
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return service.closures(id, activeOnly);
	}

	@Operation(summary = "휴장 등록", description = "[closureStart, closureEnd). 활성 휴장과 겹치면 409. "
			+ "응답의 activeReservationCount 는 구간에 걸린 활성 예약 수(자동 취소하지 않음)")
	@PostMapping("/{id}/closures")
	@ResponseStatus(HttpStatus.CREATED)
	public ClosureResponse addClosure(@PathVariable long id, @Valid @RequestBody ClosureRequest r) {
		return service.addClosure(id, r);
	}

	@Operation(summary = "휴장 해제", description = "is_active=false 로 바꾸고 행은 이력으로 남긴다")
	@DeleteMapping("/{id}/closures/{closureId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivateClosure(@PathVariable long id, @PathVariable long closureId) {
		service.deactivateClosure(id, closureId);
	}
}
