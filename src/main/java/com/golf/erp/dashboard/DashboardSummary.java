package com.golf.erp.dashboard;

import java.math.BigDecimal;

/**
 * todayReservations: 오늘 플레이 예약(취소/실패 제외), pendingReservations: 대기·결제 진행 중 예약,
 * todayTeeTimes/todayOpenTeeTimes: 오늘 티타임 수/예약 가능 수, todayPaidAmount: 오늘 결제 완료 금액
 */
public record DashboardSummary(long todayReservations, long registeredMembers, long operatingCourses,
		long pendingReservations, long todayTeeTimes, long todayOpenTeeTimes, BigDecimal todayPaidAmount) {
}
