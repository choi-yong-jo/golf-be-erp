package com.golf.erp.rental;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentRentalHistoryRepository extends JpaRepository<EquipmentRentalHistory, Long> {
	List<EquipmentRentalHistory> findByEquipmentRentalIdOrderByOccurredAtAscIdAsc(Long equipmentRentalId);
}
