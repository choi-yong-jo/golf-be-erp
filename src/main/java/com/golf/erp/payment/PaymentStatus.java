package com.golf.erp.payment;

/**
 * 결제 상태(ck_payment_status). ERP 현장 결제는 PAID 로 만들고 환불 시 같은 행을 REFUNDED 로 바꾼다. 그 밖의 값은 예약 앱·이전
 * 시스템이 쓴다(SUCCEEDED 는 PAID 와 같은 뜻).
 */
public enum PaymentStatus {
	READY, PENDING, SUCCEEDED, PAID, FAILED, CANCELLED, REFUNDED
}
