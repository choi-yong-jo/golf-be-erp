package com.golf.erp.rental;

/**
 * 재고 수량 이동 규칙. 수량 1개당 각 칸(총·가용·대여 중·정비 중)의 증감과 기록할 변동 유형을 정의한다.
 * 모든 이동은 total = available + rented + maintenance 를 유지한다.
 */
enum StockMovement {
	/** 입고: 총·가용 증가 */
	STOCK_IN(StockTxnType.STOCK_IN, 1, 1, 0, 0),
	/** 렌탈 출고: 가용 → 대여 중 */
	RENT_OUT(StockTxnType.RENT_OUT, 0, -1, 1, 0),
	/** 정상 반납: 대여 중 → 가용 */
	RETURN(StockTxnType.RETURN, 0, 1, -1, 0),
	/** 파손 반납: 대여 중 → 정비 중 (변동 유형은 RETURN) */
	RETURN_DAMAGED(StockTxnType.RETURN, 0, 0, -1, 1),
	/** 정비 보냄: 가용 → 정비 중 */
	MAINTENANCE_OUT(StockTxnType.MAINTENANCE_OUT, 0, -1, 0, 1),
	/** 정비 완료: 정비 중 → 가용 */
	MAINTENANCE_IN(StockTxnType.MAINTENANCE_IN, 0, 1, 0, -1),
	/** 분실·폐기(창고 보관분): 가용에서 차감, 총 감소 */
	LOSS_FROM_AVAILABLE(StockTxnType.LOSS, -1, -1, 0, 0),
	/** 폐기(정비 중이던 것): 정비 중에서 차감, 총 감소 */
	LOSS_FROM_MAINTENANCE(StockTxnType.LOSS, -1, 0, 0, -1),
	/** 렌탈 중 분실: 대여 중에서 차감, 총 감소 */
	LOSS_FROM_RENTED(StockTxnType.LOSS, -1, 0, -1, 0);

	final StockTxnType txnType;
	final int total;
	final int available;
	final int rented;
	final int maintenance;

	StockMovement(StockTxnType txnType, int total, int available, int rented, int maintenance) {
		this.txnType = txnType;
		this.total = total;
		this.available = available;
		this.rented = rented;
		this.maintenance = maintenance;
	}
}
