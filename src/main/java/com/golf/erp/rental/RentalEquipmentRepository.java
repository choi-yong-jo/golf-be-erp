package com.golf.erp.rental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RentalEquipmentRepository
		extends JpaRepository<RentalEquipment, Long>, JpaSpecificationExecutor<RentalEquipment> {
	boolean existsByCode(String code);
}
