package com.golf.erp.rental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RentalEquipmentStockTxnRepository
		extends JpaRepository<RentalEquipmentStockTxn, Long>, JpaSpecificationExecutor<RentalEquipmentStockTxn> {
}
