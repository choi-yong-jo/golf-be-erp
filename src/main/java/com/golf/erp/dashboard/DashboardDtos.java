package com.golf.erp.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 대시보드 응답 */
public final class DashboardDtos {
	private DashboardDtos() {
	}

	/**
	 * @param pendingReservations 처리 대기 예약(PENDING·PENDING_PAYMENT·PAYING, 날짜 무관)
	 * @param activeMembers       ACTIVE 회원 수
	 */
	public record DashboardSummary(LocalDate date, ReservationStats reservations, TeeTimeStats teeTimes,
			RoundStats rounds, Revenue revenue, long pendingReservations, long activeMembers,
			List<RecentReservation> recentReservations) {
	}

	/**
	 * 그날 플레이 예약.
	 * @param total   취소·실패를 뺀 예약 수
	 * @param players 취소·실패·노쇼를 뺀 인원 합계
	 * @param byStatus 상태별 예약 수(없는 상태는 0)
	 */
	public record ReservationStats(long total, long players, Map<String, Long> byStatus) {
	}

	/** occupancyRate: 예약된 티타임 / (OPEN + BOOKED) x 100 (소수 1자리, 운영 티타임이 없으면 0) */
	public record TeeTimeStats(long total, long open, long booked, long blocked, long closed,
			BigDecimal occupancyRate) {
	}

	/** 그날 라운드. inProgress = CHECKED_IN + PLAYING */
	public record RoundStats(long checkedIn, long playing, long finished, long cancelled, long inProgress) {
	}

	/**
	 * 그날 매출. reservationPayments: 예약 결제(completed_at 기준, 현재 PAID·SUCCEEDED 인 건 = 환불분 제외), roundPayments: 라운드
	 * 현장 수납(paid_at 기준 PAID, 예약 선결제를 옮겨 적은 PREPAID 는 중복이라 제외)
	 */
	public record Revenue(BigDecimal reservationPayments, BigDecimal roundPayments, BigDecimal total) {
	}

	public record RecentReservation(Long id, String reservationNo, String memberName, String golfCourseName,
			LocalDateTime playAt, int playerCount, String status, LocalDateTime reservedAt) {
	}
}
