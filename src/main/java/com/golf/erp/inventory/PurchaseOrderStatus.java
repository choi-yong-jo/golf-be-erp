package com.golf.erp.inventory;

/**
 * 발주 상태(ck_purchase_order_status).
 * DRAFT(작성 중, 수정 가능) → ORDERED(발주) → PARTIAL(일부 입고) → RECEIVED(전량 입고).
 * CANCELLED 는 DRAFT·ORDERED 에서만(입고가 한 건이라도 있으면 불가).
 */
public enum PurchaseOrderStatus {
	DRAFT, ORDERED, PARTIAL, RECEIVED, CANCELLED
}
