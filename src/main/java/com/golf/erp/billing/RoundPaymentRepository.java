package com.golf.erp.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface RoundPaymentRepository extends JpaRepository<RoundPayment, Long> {
	List<RoundPayment> findByRoundIdOrderById(Long roundId);

	List<RoundPayment> findByRoundPlayerId(Long roundPlayerId);

	/** 취소되지 않은 수납 합계 */
	@Query("select coalesce(sum(p.amount), 0) from RoundPayment p where p.roundId = :roundId"
			+ " and p.status = com.golf.erp.billing.PaymentStatus.PAID")
	BigDecimal sumPaid(Long roundId);
}
