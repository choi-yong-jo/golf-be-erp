package com.golf.erp.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결제 목록 한 줄 (예약 앱 온라인 결제 포함) */
public record PaymentRow(Long paymentId, Long reservationId, String reservationNo, String memberName,
		String paymentType, String paymentStatus, String paymentMethod, String pgProvider, BigDecimal amount,
		LocalDateTime requestedAt, LocalDateTime completedAt, LocalDateTime refundedAt, String failureReason) {
}
