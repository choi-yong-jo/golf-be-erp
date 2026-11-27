package com.golf.erp.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long>, JpaSpecificationExecutor<StoreOrder> {
	/** 주문 변경(항목·상태·결제)은 주문 행을 잠그고 한다. 동시에 들어온 결제·취소·항목 추가가 금액을 어긋나게 하지 않도록. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from StoreOrder o where o.id = :id")
	Optional<StoreOrder> findByIdForUpdate(@Param("id") Long id);
}
