package com.golf.erp.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface GolfCourseClosureRepository extends JpaRepository<GolfCourseClosure, Long> {
	List<GolfCourseClosure> findByGolfCourseIdOrderByClosureStartDesc(Long golfCourseId);

	List<GolfCourseClosure> findByGolfCourseIdAndActiveTrueOrderByClosureStartDesc(Long golfCourseId);

	/** [from, to) 와 겹치는 활성 휴장 */
	@Query("""
			select c from GolfCourseClosure c
			where c.golfCourseId = :golfCourseId and c.active = true
			  and c.closureStart < :to and c.closureEnd > :from
			order by c.closureStart""")
	List<GolfCourseClosure> findActiveOverlapping(Long golfCourseId, LocalDateTime from, LocalDateTime to);
}
