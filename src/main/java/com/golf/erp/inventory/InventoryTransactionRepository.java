package com.golf.erp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 재고 수불 원장. 현재고 = Σ(입고·반납·조정 증가) − Σ(출고). */
public interface InventoryTransactionRepository
		extends JpaRepository<InventoryTransaction, Long>, JpaSpecificationExecutor<InventoryTransaction> {

	@Query("select sum(case when t.type = :issue then -t.quantity else t.quantity end) from InventoryTransaction t"
			+ " where t.inventoryItemId = :itemId")
	BigDecimal sumStock(@Param("itemId") long itemId, @Param("issue") InventoryTransactionType issue);

	@Query("select t.inventoryItemId, sum(case when t.type = :issue then -t.quantity else t.quantity end)"
			+ " from InventoryTransaction t where t.inventoryItemId in :itemIds group by t.inventoryItemId")
	List<Object[]> sumStocks(@Param("itemIds") Collection<Long> itemIds,
			@Param("issue") InventoryTransactionType issue);

	/** 품목 하나의 현재고(거래가 없으면 0) */
	default BigDecimal stockOf(long itemId) {
		BigDecimal s = sumStock(itemId, InventoryTransactionType.ISSUE);
		return s == null ? BigDecimal.ZERO : s;
	}

	/** 품목별 현재고. 거래가 없는 품목은 맵에 없다(0 으로 본다). */
	default Map<Long, BigDecimal> stocksOf(Collection<Long> itemIds) {
		Map<Long, BigDecimal> m = new HashMap<>();
		if (itemIds == null || itemIds.isEmpty())
			return m;
		for (Object[] row : sumStocks(itemIds, InventoryTransactionType.ISSUE))
			m.put((Long) row[0], row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1]);
		return m;
	}
}
