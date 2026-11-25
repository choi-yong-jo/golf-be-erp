package com.golf.erp.dashboard;

import java.time.LocalDateTime;

public record RecentReservation(Long reservationId, String reservationNo, String memberName, String courseName,
		LocalDateTime playAt, String reservationStatus, LocalDateTime reservedAt) {
}
