package com.golf.erp.payment;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.payment (예약 결제·환불). 이 모듈은 조회만 한다. 결제 등록·환불은 예약 처리(현장 결제·취소)와 한 트랜잭션으로 묶여야 해서
 * 예약 모듈이 SQL 로 쓴다. ERP 현장 결제는 pg_provider=ERP, pg_transaction_id=ERP-{uuid}, 상태 PAID 다
 * (ck_payment_paid: PAID 는 completed_at·pg_transaction_id 필수).
 */
@Entity
@Immutable
@Table(name = "payment")
public class Payment {
	public static final String ERP_PROVIDER = "ERP";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_id")
	private Long id;
	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_type", nullable = false, length = 20)
	private PaymentType type;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private PaymentStatus status;
	@Column(name = "payment_method", length = 30)
	private String method;
	@Column(name = "pg_provider", length = 50)
	private String pgProvider;
	@Column(name = "pg_transaction_id", length = 200)
	private String pgTransactionId;
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;
	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Column(name = "refunded_at")
	private LocalDateTime refundedAt;
	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	protected Payment() {
	}

	public boolean isErp() {
		return ERP_PROVIDER.equals(pgProvider);
	}

	public Long getId() {
		return id;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public PaymentType getType() {
		return type;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getMethod() {
		return method;
	}

	public String getPgProvider() {
		return pgProvider;
	}

	public String getPgTransactionId() {
		return pgTransactionId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public LocalDateTime getRefundedAt() {
		return refundedAt;
	}

	public String getFailureReason() {
		return failureReason;
	}
}
