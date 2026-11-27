package com.golf.erp.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PromotionRedemptionRepository
		extends JpaRepository<PromotionRedemption, Long>, JpaSpecificationExecutor<PromotionRedemption> {
	boolean existsByPromotionIdAndReservationId(Long promotionId, Long reservationId);
}
