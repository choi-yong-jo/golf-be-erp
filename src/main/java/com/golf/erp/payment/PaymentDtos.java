package com.golf.erp.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결제 내역 API 응답 */
public final class PaymentDtos {
	private PaymentDtos() {
	}

	/** 결제가 속한 예약 요약(예약·회원 테이블에서 SQL 로 읽음) */
	public record ReservationSummary(Long reservationId, String reservationNo, String reservationStatus,
			Long memberId, String memberName, String memberPhone, BigDecimal totalAmount, LocalDateTime playAt) {
	}

	/** erp: ERP 현장 결제(pg_provider=ERP) 여부. 그 밖의 결제는 예약 앱 온라인(PG) 결제다. */
	public record PaymentResponse(Long id, Long reservationId, String reservationNo, String memberName,
			PaymentType type, PaymentStatus status, String method, String pgProvider, String pgTransactionId,
			boolean erp, BigDecimal amount, LocalDateTime requestedAt, LocalDateTime completedAt,
			LocalDateTime refundedAt, String failureReason) {

		static PaymentResponse of(Payment p, ReservationSummary r) {
			return new PaymentResponse(p.getId(), p.getReservationId(), r == null ? null : r.reservationNo(),
					r == null ? null : r.memberName(), p.getType(), p.getStatus(), p.getMethod(), p.getPgProvider(),
					p.getPgTransactionId(), p.isErp(), p.getAmount(), p.getRequestedAt(), p.getCompletedAt(),
					p.getRefundedAt(), p.getFailureReason());
		}
	}

	public record PaymentDetail(PaymentResponse payment, ReservationSummary reservation) {
	}
}
