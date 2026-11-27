package com.golf.erp.round;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoundCourseRepository extends JpaRepository<RoundCourse, Long> {
	List<RoundCourse> findByRoundIdOrderBySeqNo(Long roundId);

	/** 지금 사용 중인 코스 구간(라운드당 최대 하나, ux_round_course_open) */
	Optional<RoundCourse> findByRoundIdAndFinishedAtIsNull(Long roundId);
}
