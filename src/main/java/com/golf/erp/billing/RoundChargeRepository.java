package com.golf.erp.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface RoundChargeRepository extends JpaRepository<RoundCharge, Long> {
	List<RoundCharge> findByRoundIdOrderById(Long roundId);

	List<RoundCharge> findByRoundPlayerId(Long roundPlayerId);

	/** 취소되지 않은 요금 합계 */
	@Query("select coalesce(sum(c.amount), 0) from RoundCharge c where c.roundId = :roundId and c.voided = false")
	BigDecimal sumActive(Long roundId);
}
