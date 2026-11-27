package com.golf.erp.round;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoundEventRepository extends JpaRepository<RoundEvent, Long> {
	List<RoundEvent> findByRoundIdOrderByOccurredAtAscIdAsc(Long roundId);

	/** 참가자를 라운드에서 뺄 때 그 참가자를 가리키던 이력의 참조를 끊는다(메시지에 이름이 남아 있다). */
	@Modifying(flushAutomatically = true)
	@Query("update RoundEvent e set e.roundPlayerId = null where e.roundPlayerId = :roundPlayerId")
	int detachPlayer(Long roundPlayerId);
}
