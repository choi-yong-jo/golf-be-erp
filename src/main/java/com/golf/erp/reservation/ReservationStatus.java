package com.golf.erp.reservation;

import java.util.EnumSet;
import java.util.Set;

/**
 * DB CHECK ck_reservation_status 와 동일.
 * <ul>
 * <li>ERP: PENDING(현장/전화 예약) → CONFIRMED → COMPLETED / NO_SHOW, 또는 CANCELLED</li>
 * <li>예약 앱: PENDING_PAYMENT → PAYING → CONFIRMED, 취소 시 REFUND_PENDING → CANCELLED</li>
 * </ul>
 */
public enum ReservationStatus {
	PENDING, PENDING_PAYMENT, PAYING, CONFIRMED, REFUND_PENDING, CANCELLED, COMPLETED, NO_SHOW, FAILED;

	/** 티타임을 점유하는 상태 (ux_reservation_active_tee_time 과 동일) */
	public static final Set<ReservationStatus> ACTIVE = EnumSet.of(PENDING, PENDING_PAYMENT, PAYING, CONFIRMED);

	/** 예약 앱이 결제/환불을 진행 중인 상태. ERP는 건드리지 않는다. */
	public static final Set<ReservationStatus> IN_PROGRESS_BY_APP = EnumSet.of(PAYING, REFUND_PENDING);
}
