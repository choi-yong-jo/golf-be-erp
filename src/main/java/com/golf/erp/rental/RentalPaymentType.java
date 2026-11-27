package com.golf.erp.rental;

/** 렌탈 결제 유형 (equipment_rental_payment.payment_type CHECK). REFUND 는 돌려준 금액(주로 보증금 반환) */
public enum RentalPaymentType {
	RENTAL_FEE, DEPOSIT, DAMAGE_FEE, REFUND
}
