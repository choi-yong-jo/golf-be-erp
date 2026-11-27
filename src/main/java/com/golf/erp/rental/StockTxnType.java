package com.golf.erp.rental;

/**
 * 렌탈 재고 변동 유형 (rental_equipment_stock_txn.transaction_type CHECK).
 * RENT_OUT·RETURN 은 렌탈 출고·반납이 자동으로 기록하고, 나머지는 재고 담당자가 직접 처리한다(LOSS 는 렌탈 분실에서도 기록).
 */
public enum StockTxnType {
	STOCK_IN, RENT_OUT, RETURN, MAINTENANCE_OUT, MAINTENANCE_IN, LOSS, ADJUSTMENT;

	/** 재고 화면에서 직접 처리할 수 있는 유형 */
	boolean isManual() {
		return this != RENT_OUT && this != RETURN;
	}
}
