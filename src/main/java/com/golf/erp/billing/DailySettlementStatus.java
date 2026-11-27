package com.golf.erp.billing;

/** 일 마감 상태. CLOSED 면 그날 라운드의 요금·수납을 바꿀 수 없다. */
public enum DailySettlementStatus {
	OPEN, CLOSED
}
