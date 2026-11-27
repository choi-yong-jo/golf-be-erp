package com.golf.erp.facility;

/**
 * 시설 예약 수납 상태(facility_booking.payment_status). 결제 테이블이 따로 없어 직원이 수납 결과를 기록한다.
 * REFUNDED 는 돈을 받은 적이 있는(PARTIAL·PAID) 예약에만 기록할 수 있다.
 */
public enum FacilityPaymentStatus {
	UNPAID, PARTIAL, PAID, REFUNDED
}
