package com.golf.erp.course;

import com.golf.erp.course.CourseDtos.CourseCreateRequest;
import com.golf.erp.course.CourseDtos.CourseResponse;
import com.golf.erp.course.CourseDtos.CourseStatusRequest;
import com.golf.erp.course.CourseDtos.CourseUpdateRequest;
import com.golf.erp.course.CourseDtos.HoleBulkRequest;
import com.golf.erp.course.CourseDtos.HoleResponse;
import com.golf.erp.course.CourseDtos.HoleUpdateRequest;
import com.golf.erp.course.CourseDtos.ScorecardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "코스·홀")
@RestController
@RequestMapping("/api")
public class CourseController {
	private final CourseService service;

	public CourseController(CourseService service) {
		this.service = service;
	}

	@Operation(summary = "코스 목록", description = "골프장 안의 9홀 단위 코스(OUT, IN ...). 표시 순서순, 페이지 없음. 파 합계 포함")
	@GetMapping("/courses")
	public List<CourseResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) Boolean active, @RequestParam(required = false) CourseStatus status) {
		return service.list(golfCourseId, active, status);
	}

	@GetMapping("/courses/{id}")
	public CourseResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "코스 등록", description = "courseCode 는 대문자로 저장되며 티타임의 course 값으로 쓰인다")
	@PostMapping("/courses")
	@ResponseStatus(HttpStatus.CREATED)
	public CourseResponse create(@Valid @RequestBody CourseCreateRequest r) {
		return service.create(r);
	}

	@Operation(summary = "코스 수정", description = "티타임에 쓰이는 코스 코드는 바꿀 수 없다(409)")
	@PutMapping("/courses/{id}")
	public CourseResponse update(@PathVariable long id, @Valid @RequestBody CourseUpdateRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "코스 상태 변경", description = "OPEN, MAINTENANCE, CLOSED. active 를 주면 활성 여부도 바꾼다")
	@PatchMapping("/courses/{id}/status")
	public CourseResponse changeStatus(@PathVariable long id, @Valid @RequestBody CourseStatusRequest r) {
		return service.changeStatus(id, r);
	}

	@Operation(summary = "코스 비활성", description = "물리 삭제 대신 is_active=false")
	@DeleteMapping("/courses/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable long id) {
		service.deactivate(id);
	}

	@Operation(summary = "코스 스코어카드(홀 목록)")
	@GetMapping("/courses/{id}/holes")
	public ScorecardResponse holes(@PathVariable long id) {
		return service.holes(id);
	}

	@Operation(summary = "스코어카드 일괄 저장", description = "홀 번호 기준 등록·수정. 보내지 않은 기존 홀은 유지. 홀 번호 ≤ 코스 홀 수")
	@PutMapping("/courses/{id}/holes")
	public ScorecardResponse saveHoles(@PathVariable long id, @Valid @RequestBody HoleBulkRequest r) {
		return service.saveHoles(id, r);
	}

	@PutMapping("/holes/{id}")
	public HoleResponse updateHole(@PathVariable long id, @Valid @RequestBody HoleUpdateRequest r) {
		return service.updateHole(id, r);
	}
}
