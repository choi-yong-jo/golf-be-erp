package com.golf.erp.store;

/** 매장 주문 결제 상태 (store_order.payment_status, ck_store_order_payment). UNPAID → PAID → REFUNDED */
public enum StorePaymentStatus {
	UNPAID, PAID, REFUNDED
}
