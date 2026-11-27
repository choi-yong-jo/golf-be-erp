package com.golf.erp.equipment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;

public interface EquipmentMaintenanceRepository
		extends JpaRepository<EquipmentMaintenance, Long>, JpaSpecificationExecutor<EquipmentMaintenance> {
	boolean existsByEquipmentIdAndStatusIn(Long equipmentId, Collection<MaintenanceStatus> statuses);

	boolean existsByEquipmentId(Long equipmentId);
}
