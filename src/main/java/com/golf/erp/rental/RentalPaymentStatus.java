package com.golf.erp.rental;

/**
 * 렌탈 결제 상태 (equipment_rental_payment.payment_status CHECK).
 * 환불은 REFUND 유형의 별도 행으로 기록하므로 이 모듈은 PAID·CANCELLED 만 쓴다(REFUNDED 는 DDL 호환용).
 */
public enum RentalPaymentStatus {
	PAID, CANCELLED, REFUNDED
}
