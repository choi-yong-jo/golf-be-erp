package com.golf.erp.dashboard;

import java.time.LocalDateTime;

public record RecentReservation(String reservationNo, String memberName, String courseName, LocalDateTime reservedAt,
		String status) {
}
