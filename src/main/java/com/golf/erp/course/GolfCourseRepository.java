package com.golf.erp.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GolfCourseRepository extends JpaRepository<GolfCourse, Long> {
	boolean existsByCourseCode(String courseCode);

	boolean existsByCourseCodeAndGolfCourseIdNot(String courseCode, Long golfCourseId);

	List<GolfCourse> findAllByOrderByGolfCourseIdAsc();
}
