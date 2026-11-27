package com.golf.erp.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {
	boolean existsByGolfCourseIdAndCourseCode(Long golfCourseId, String courseCode);

	boolean existsByGolfCourseIdAndCourseCodeAndIdNot(Long golfCourseId, String courseCode, Long id);
}
