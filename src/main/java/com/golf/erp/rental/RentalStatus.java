package com.golf.erp.rental;

import java.util.EnumSet;
import java.util.Set;

/**
 * 렌탈 상태 (equipment_rental.rental_status CHECK).
 * RESERVED → RENTED → (PARTIALLY_RETURNED) → RETURNED, RESERVED → CANCELLED.
 * 반납 기한이 지나면 RENTED·PARTIALLY_RETURNED → OVERDUE (반납 처리는 RENTED 와 같다).
 */
public enum RentalStatus {
	RESERVED, RENTED, PARTIALLY_RETURNED, RETURNED, CANCELLED, OVERDUE;

	/** 장비가 고객에게 나가 있는 상태 */
	static final Set<RentalStatus> OUT = EnumSet.of(RENTED, PARTIALLY_RETURNED, OVERDUE);

	boolean isOut() {
		return OUT.contains(this);
	}
}
