package com.golf.erp.payment;

/** DB CHECK ck_payment_status 와 동일. ERP는 PAID, REFUNDED 만 쓴다. */
public enum PaymentStatus {
	READY, PENDING, SUCCEEDED, PAID, FAILED, CANCELLED, REFUNDED
}
