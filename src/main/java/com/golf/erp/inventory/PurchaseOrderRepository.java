package com.golf.erp.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository
		extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

	/** 상태·입고 수량을 바꾸는 처리용 잠금(동시 입고로 발주 수량을 넘지 않도록) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PurchaseOrder p where p.id = :id")
	Optional<PurchaseOrder> findByIdForUpdate(@Param("id") long id);
}
