package com.golf.erp.lodging;

/** 숙박 예약 수납 상태(lodging_booking.payment_status). REFUNDED 는 받은 금액이 있을 때(PARTIAL·PAID)만. */
public enum LodgingPaymentStatus {
	UNPAID, PARTIAL, PAID, REFUNDED
}
