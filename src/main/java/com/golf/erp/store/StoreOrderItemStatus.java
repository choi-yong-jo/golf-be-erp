package com.golf.erp.store;

/**
 * 주문 항목 상태 (store_order_item.item_status, ck_store_order_item_status).
 * ORDERED 주문 → PREPARING 조리 중 → READY 준비 완료 → SERVED 제공 완료. CANCELLED 취소(소계에서 빠진다).
 */
public enum StoreOrderItemStatus {
	ORDERED, PREPARING, READY, SERVED, CANCELLED
}
