package com.golf.erp.payment;

/** 결제 유형(ck_payment_type). REFUND 는 예약 앱이 환불을 별도 행으로 남길 때 쓴다(ERP 는 만들지 않음). */
public enum PaymentType {
	PAYMENT, REFUND
}
