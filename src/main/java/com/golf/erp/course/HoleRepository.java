package com.golf.erp.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface HoleRepository extends JpaRepository<Hole, Long> {
	List<Hole> findByCourseIdOrderByHoleNo(Long courseId);

	/** 코스별 [course_id, 파 합계, 등록된 홀 수, 가장 큰 홀 번호] */
	@Query("""
			select h.courseId, sum(h.par), count(h), max(h.holeNo) from Hole h
			where h.courseId in :courseIds
			group by h.courseId""")
	List<Object[]> summarize(Collection<Long> courseIds);
}
