package com.golf.erp.member;

/** 회원 상태(ck_member_status). 회원은 예약·결제 이력과 연결되어 있어 삭제하지 않고 상태로 관리한다. */
public enum MemberStatus {
	ACTIVE, SUSPENDED, WITHDRAWN
}
