package com.golf.erp.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 예약 목록 한 줄 (예약 앱이 만든 예약 포함) */
public record ReservationRow(Long reservationId, String reservationNo, Long memberId, String memberName,
		String memberPhone, Long golfCourseId, String courseName, Long teeTimeId, String course, LocalDateTime playAt,
		Integer playerCount, String reservationStatus, BigDecimal totalAmount, LocalDateTime reservedAt) {
}
