package com.golf.erp.accounting;

/** 운영비 결재 상태 (operating_expense.approval_status). PENDING → APPROVED → PAID, PENDING → REJECTED */
public enum ExpenseStatus {
	PENDING, APPROVED, REJECTED, PAID
}
