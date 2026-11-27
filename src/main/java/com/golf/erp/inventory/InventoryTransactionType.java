package com.golf.erp.inventory;

/**
 * 재고 수불 유형(ck_inventory_transaction_type). quantity 는 항상 양수(CHECK quantity &gt; 0)이고 방향은 유형으로 정한다.
 * <ul>
 * <li>RECEIPT: 입고(+). 발주 입고 또는 직접 입고</li>
 * <li>RETURN: 반납(+). 영업장에 내준 재고가 창고로 돌아온 것. 거래처 반품은 ISSUE 로 기록한다</li>
 * <li>ADJUSTMENT: 실사 조정 중 <b>증가분만</b>(+)</li>
 * <li>ISSUE: 출고(−). 실사 조정 감소분도 ISSUE 로 저장하고 memo 앞에 {@link #ADJUSTMENT_MEMO_PREFIX} 를 붙인다</li>
 * </ul>
 * 현재고 = Σ(RECEIPT, RETURN, ADJUSTMENT) − Σ(ISSUE).
 */
public enum InventoryTransactionType {
	RECEIPT, ISSUE, ADJUSTMENT, RETURN;

	/** 실사 조정 감소를 ISSUE 로 저장할 때 memo 앞에 붙는 표시 */
	public static final String ADJUSTMENT_MEMO_PREFIX = "[조정]";

	public boolean increases() {
		return this != ISSUE;
	}
}
