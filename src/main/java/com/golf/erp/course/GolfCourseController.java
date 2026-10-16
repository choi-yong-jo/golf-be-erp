package com.golf.erp.course;

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
		return service.find(id);
	}
}
