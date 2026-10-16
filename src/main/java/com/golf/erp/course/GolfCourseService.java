package com.golf.erp.course;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class GolfCourseService {
	private final GolfCourseRepository repo;

	public GolfCourseService(GolfCourseRepository repo) {
		this.repo = repo;
	}

	@Transactional(readOnly = true)
	public List<GolfCourse> findAll() {
		return repo.findAll();
	}

	@Transactional(readOnly = true)
	public GolfCourse find(long id) {
		return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("코스를 찾을 수 없습니다: " + id));
	}
}
