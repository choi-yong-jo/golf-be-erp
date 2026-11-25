package com.golf.erp.course;

import com.golf.erp.course.dto.ClosureRequest;
import com.golf.erp.course.dto.CourseRequest;
import com.golf.erp.course.dto.CourseStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class GolfCourseController {
	private final GolfCourseService service;

	public GolfCourseController(GolfCourseService service) {
		this.service = service;
	}

	@GetMapping
	public List<GolfCourse> list() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public GolfCourse get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GolfCourse create(@Valid @RequestBody CourseRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public GolfCourse update(@PathVariable long id, @Valid @RequestBody CourseRequest r) {
		return service.update(id, r);
	}

	@PatchMapping("/{id}/status")
	public GolfCourse changeStatus(@PathVariable long id, @Valid @RequestBody CourseStatusRequest r) {
		return service.changeStatus(id, r);
	}

	@GetMapping("/{id}/closures")
	public List<GolfCourseClosure> closures(@PathVariable long id) {
		return service.closures(id);
	}

	@PostMapping("/{id}/closures")
	@ResponseStatus(HttpStatus.CREATED)
	public GolfCourseClosure addClosure(@PathVariable long id, @Valid @RequestBody ClosureRequest r) {
		return service.addClosure(id, r);
	}

	/** 휴장 해제 (is_active=false) */
	@DeleteMapping("/{id}/closures/{closureId}")
	public GolfCourseClosure deactivateClosure(@PathVariable long id, @PathVariable long closureId) {
		return service.deactivateClosure(id, closureId);
	}
}
