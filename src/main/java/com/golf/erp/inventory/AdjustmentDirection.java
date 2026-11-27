package com.golf.erp.inventory;

/**
 * 실사 조정 방향(요청 전용, DB 컬럼 아님). DDL 에 부호 컬럼이 없고 quantity 는 양수만 허용하므로
 * INCREASE 는 ADJUSTMENT 로, DECREASE 는 ISSUE + memo "[조정] ..." 으로 저장한다.
 */
public enum AdjustmentDirection {
	INCREASE, DECREASE
}
