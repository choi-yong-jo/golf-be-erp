package com.golf.erp.settlement;

/** 일별 정산 상태. OPEN: 재계산 가능, CLOSED: 마감(해제 전까지 재계산하지 않음). DDL 에 CHECK 는 없다. */
public enum SettlementStatus {
	OPEN, CLOSED
}
