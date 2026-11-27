package com.golf.erp.store;

/**
 * 입점 매장 운영 상태 (tenant_store.store_status, ck_tenant_store_status).
 * ACTIVE 영업 중, INACTIVE 휴점, SUSPENDED 영업 정지, CLOSED 폐점(최종 상태 — 더 바꿀 수 없음).
 */
public enum StoreStatus {
	ACTIVE, INACTIVE, SUSPENDED, CLOSED
}
