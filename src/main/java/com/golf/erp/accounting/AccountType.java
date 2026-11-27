package com.golf.erp.accounting;

/** 계정 유형 (account.account_type) */
public enum AccountType {
	ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE;

	/** 차변이 늘어나는 쪽(자산·비용)이면 true. 잔액 = 차변 - 대변, 아니면 대변 - 차변 */
	public boolean isDebitNormal() {
		return this == ASSET || this == EXPENSE;
	}
}
