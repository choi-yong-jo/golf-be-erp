package com.golf.erp.reservation;

import java.time.LocalDateTime;

public record ReservationRow(Long id, String reservationNo, String memberName, String courseName,
		LocalDateTime reservedAt, Integer playerCount, String status) {
}
