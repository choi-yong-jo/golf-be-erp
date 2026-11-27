package com.golf.erp.store;

/**
 * 매장 주문 진행 상태 (store_order.order_status, ck_store_order_status).
 * PLACED 접수 → ACCEPTED 확인 → PREPARING 준비 중 → READY 준비 완료 → COMPLETED 완료. CANCELLED 취소.
 * COMPLETED·CANCELLED 는 최종 상태다.
 */
public enum StoreOrderStatus {
	PLACED, ACCEPTED, PREPARING, READY, COMPLETED, CANCELLED;

	/** 정상 흐름의 다음 상태. 최종 상태(COMPLETED·CANCELLED)면 null */
	StoreOrderStatus next() {
		return switch (this) {
		case PLACED -> ACCEPTED;
		case ACCEPTED -> PREPARING;
		case PREPARING -> READY;
		case READY -> COMPLETED;
		case COMPLETED, CANCELLED -> null;
		};
	}

	/** 주문 항목을 추가·취소할 수 있는 상태(접수·확인·준비 중) */
	boolean itemsEditable() {
		return this == PLACED || this == ACCEPTED || this == PREPARING;
	}

	boolean isFinal() {
		return this == COMPLETED || this == CANCELLED;
	}
}
