package com.golf.erp.facility;

import java.util.List;

/**
 * 시설 예약 상태(facility_booking.booking_status).
 * <pre>
 * PENDING ─confirm→ CONFIRMED ─check-in→ CHECKED_IN ─check-out→ CHECKED_OUT
 *    │                 │  └─no-show→ NO_SHOW
 *    └──── cancel ─────┴→ CANCELLED
 * </pre>
 */
public enum FacilityBookingStatus {
	PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW;

	/** 객실을 점유하는(겹치면 안 되는) 상태 */
	public static final List<FacilityBookingStatus> ACTIVE = List.of(PENDING, CONFIRMED, CHECKED_IN);

	/** 아직 입실 전인 상태(내용 수정·취소 가능) */
	public static final List<FacilityBookingStatus> UPCOMING = List.of(PENDING, CONFIRMED);

	public boolean isActive() {
		return ACTIVE.contains(this);
	}
}
