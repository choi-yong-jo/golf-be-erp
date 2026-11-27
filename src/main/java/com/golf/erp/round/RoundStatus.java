package com.golf.erp.round;

/** 라운드 상태. CHECKED_IN(체크인, 출발 대기) → PLAYING(코스 사용 중) → FINISHED(종료) / CANCELLED(취소) */
public enum RoundStatus {
	CHECKED_IN, PLAYING, FINISHED, CANCELLED;

	/** 체크인·플레이 중(골프장 안에 있는 팀) */
	public boolean isActive() {
		return this == CHECKED_IN || this == PLAYING;
	}
}
