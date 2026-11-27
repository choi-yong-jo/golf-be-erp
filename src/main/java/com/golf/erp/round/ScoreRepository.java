package com.golf.erp.round;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScoreRepository extends JpaRepository<Score, Long> {
	List<Score> findByRoundCourseIdAndHoleIdAndRoundPlayerIdIn(Long roundCourseId, Long holeId,
			Collection<Long> roundPlayerIds);

	List<Score> findByRoundPlayerIdIn(Collection<Long> roundPlayerIds);

	boolean existsByRoundPlayerId(Long roundPlayerId);
}
