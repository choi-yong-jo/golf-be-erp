package com.golf.erp.accounting;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OperatingExpenseRepository
		extends JpaRepository<OperatingExpense, Long>, JpaSpecificationExecutor<OperatingExpense> {
	boolean existsByAccountId(Long accountId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from OperatingExpense e where e.id = :id")
	Optional<OperatingExpense> findByIdForUpdate(@Param("id") Long id);
}
