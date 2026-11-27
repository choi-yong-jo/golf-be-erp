package com.golf.erp.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DailySettlementItemRepository extends JpaRepository<DailySettlementItem, Long> {
	List<DailySettlementItem> findByDailySettlementIdOrderByItemTypeAscItemCodeAsc(Long dailySettlementId);

	/** 다시 계산할 때 기존 항목을 먼저 지운다(유니크 키가 같은 행을 새로 넣기 전에 바로 DELETE 를 보낸다). */
	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("delete from DailySettlementItem i where i.dailySettlementId = :dailySettlementId")
	int deleteBySettlement(Long dailySettlementId);
}
