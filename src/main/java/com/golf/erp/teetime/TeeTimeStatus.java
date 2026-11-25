package com.golf.erp.teetime;

/** DB CHECK ck_tee_time_status 와 동일. BOOKED 는 예약 생성/취소로만 바뀐다. */
public enum TeeTimeStatus {
	OPEN, BOOKED, BLOCKED, CLOSED;

	public boolean isAdminSettable() {
		return this != BOOKED;
	}
}
