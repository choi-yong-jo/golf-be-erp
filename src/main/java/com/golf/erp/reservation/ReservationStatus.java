package com.golf.erp.reservation;

import java.util.EnumSet;
import java.util.Set;

/**
 * 예약 상태(ck_reservation_status).
 * <ul>
 * <li>ERP: PENDING(현장/전화 예약) → CONFIRMED → COMPLETED / NO_SHOW, 또는 CANCELLED</li>
 * <li>예약 앱: PENDING_PAYMENT → PAYING → CONFIRMED, 취소 시 REFUND_PENDING → CANCELLED, 결제 실패 FAILED</li>
 * </ul>
 */
public enum ReservationStatus {
	PENDING, PENDING_PAYMENT, PAYING, CONFIRMED, REFUND_PENDING, CANCELLED, COMPLETED, NO_SHOW, FAILED;

	/** 티타임을 점유하는 상태 (ux_reservation_active_tee_time 의 조건과 같다) */
	public static final Set<ReservationStatus> ACTIVE = EnumSet.of(PENDING, PENDING_PAYMENT, PAYING, CONFIRMED);

	/** 예약 앱이 결제·환불을 진행 중인 상태. ERP 는 이 상태의 예약을 바꾸지 않는다. */
	public static final Set<ReservationStatus> IN_PROGRESS_BY_APP = EnumSet.of(PAYING, REFUND_PENDING);

	/** 더 이상 바뀌지 않는 종료 상태 */
	public static final Set<ReservationStatus> FINISHED = EnumSet.of(CANCELLED, COMPLETED, NO_SHOW, FAILED);

	/** 라운드 체크인이 가능한 상태 */
	public static final Set<ReservationStatus> CHECK_IN_ALLOWED = EnumSet.of(PENDING, CONFIRMED);
}
