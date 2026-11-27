package com.golf.erp.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository
		extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {
	boolean existsByItemCode(String itemCode);

	boolean existsByItemCodeAndIdNot(String itemCode, Long id);

	List<InventoryItem> findByActiveTrueAndReorderLevelGreaterThanOrderByItemCode(BigDecimal reorderLevel);

	/** 출고(재고 감소) 전 현재고 확인용 잠금. 같은 품목의 감소 처리는 이 잠금으로 한 줄로 선다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from InventoryItem i where i.id = :id")
	Optional<InventoryItem> findByIdForUpdate(@Param("id") long id);
}
