package com.golf.erp.payment;

/** DB CHECK ck_payment_type 와 동일. ERP는 PAYMENT 만 만든다(환불은 같은 행을 REFUNDED 로). */
public enum PaymentType {
	PAYMENT, REFUND
}
