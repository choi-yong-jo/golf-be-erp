package com.golf.erp.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GolfCourseRepository extends JpaRepository<GolfCourse, Long>, JpaSpecificationExecutor<GolfCourse> {
	boolean existsByCourseCode(String courseCode);

	boolean existsByCourseCodeAndIdNot(String courseCode, Long id);
}
