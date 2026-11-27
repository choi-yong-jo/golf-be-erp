package com.golf.erp.lodging;

import java.util.List;

/**
 * 숙박 예약 상태(lodging_booking.booking_status).
 * <pre>
 * PENDING ─confirm→ CONFIRMED ─check-in→ CHECKED_IN ─check-out→ CHECKED_OUT
 *    │                 │  └─no-show→ NO_SHOW
 *    └──── cancel ─────┴→ CANCELLED
 * </pre>
 */
public enum LodgingBookingStatus {
	PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW;

	/** 객실을 점유하는(시간이 겹치면 안 되는) 상태 */
	public static final List<LodgingBookingStatus> ACTIVE = List.of(PENDING, CONFIRMED, CHECKED_IN);

	/** 아직 입실 전인 상태(내용 수정·취소 가능) */
	public static final List<LodgingBookingStatus> UPCOMING = List.of(PENDING, CONFIRMED);

	/** 달력에 보여 주는 상태(취소·노쇼 제외) */
	public static final List<LodgingBookingStatus> CALENDAR = List.of(PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT);
}
