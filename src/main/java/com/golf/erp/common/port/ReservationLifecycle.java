package com.golf.erp.common.port;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 조회·상태 변경(reservation 모듈이 구현). 라운드 모듈이 체크인·종료 시 예약과 연동할 때 이 인터페이스만 쓴다.
 */
public interface ReservationLifecycle {
	/**
	 * 체크인할 예약 요약. 예약이 없으면 NotFoundException, 체크인할 수 없는 상태(취소·완료·노쇼·결제 진행 중 등)면
	 * ConflictException.
	 */
	CheckInTarget checkInTarget(long reservationId);

	/** 라운드 종료 시 예약을 COMPLETED 로 바꾼다. 이미 종료 상태(COMPLETED·CANCELLED·NO_SHOW)면 아무것도 하지 않는다. */
	void markCompleted(long reservationId);

	/**
	 * @param greenFeePerPlayer 예약에 기록된 1인 그린피(없으면 null)
	 * @param paidAmount        예약 단계에서 이미 결제된 금액 합계(환불 제외, 없으면 0)
	 */
	record CheckInTarget(long reservationId, String reservationNo, Long memberId, Long teeTimeId, Long golfCourseId,
			LocalDateTime playAt, int playerCount, BigDecimal greenFeePerPlayer, BigDecimal paidAmount,
			List<Player> players) {
	}

	record Player(int order, String name, String phone, Long memberId) {
	}
}
