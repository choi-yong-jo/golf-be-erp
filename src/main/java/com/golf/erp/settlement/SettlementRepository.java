package com.golf.erp.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {
	Optional<Settlement> findBySettlementDate(LocalDate date);

	List<Settlement> findBySettlementDateBetweenOrderBySettlementDateDesc(LocalDate from, LocalDate to);
}
