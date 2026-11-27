package com.golf.erp.equipment;

import java.util.EnumSet;
import java.util.Set;

/** 정비 상태 (equipment_maintenance.maintenance_status). OPEN → IN_PROGRESS → COMPLETED, 진행 중에는 CANCELLED 가능 */
public enum MaintenanceStatus {
	OPEN, IN_PROGRESS, COMPLETED, CANCELLED;

	/** 아직 끝나지 않은 정비(장비를 MAINTENANCE 로 묶어 두는 상태) */
	public static final Set<MaintenanceStatus> ACTIVE = EnumSet.of(OPEN, IN_PROGRESS);

	public boolean isClosed() {
		return this == COMPLETED || this == CANCELLED;
	}
}
