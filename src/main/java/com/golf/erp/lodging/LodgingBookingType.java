package com.golf.erp.lodging;

/**
 * 숙박 예약 유형(lodging_booking.booking_type).
 * <ul>
 * <li>DAY_USE: 대실. 입실 시각부터 day_use_hours 시간(기본은 객실 타입의 대실 시간)</li>
 * <li>OVERNIGHT: 1박. 당일 16:00 입실 → 익일 11:00 퇴실. 행 하나가 정확히 1박이다(DDL CHECK).</li>
 * </ul>
 */
public enum LodgingBookingType {
	DAY_USE, OVERNIGHT
}
