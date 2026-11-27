package com.golf.erp.rental;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentRentalPaymentRepository extends JpaRepository<EquipmentRentalPayment, Long> {
	List<EquipmentRentalPayment> findByEquipmentRentalIdOrderByPaidAtAscIdAsc(Long equipmentRentalId);

	Optional<EquipmentRentalPayment> findByIdAndEquipmentRentalId(Long id, Long equipmentRentalId);
}
