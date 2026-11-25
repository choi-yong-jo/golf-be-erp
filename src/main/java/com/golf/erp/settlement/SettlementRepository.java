package com.golf.erp.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
	Optional<Settlement> findBySettlementDate(LocalDate date);

	List<Settlement> findBySettlementDateBetweenOrderBySettlementDateDesc(LocalDate from, LocalDate to);
}
