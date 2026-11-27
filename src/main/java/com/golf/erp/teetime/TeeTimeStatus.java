package com.golf.erp.teetime;

/** DB CHECK ck_tee_time_status 와 같다. BOOKED 는 예약 모듈(예약 생성·취소)만 바꾼다. */
public enum TeeTimeStatus {
	OPEN, BOOKED, BLOCKED, CLOSED;

	/** 관리자가 직접 지정할 수 있는 상태 */
	public boolean isAdminSettable() {
		return this != BOOKED;
	}
}
