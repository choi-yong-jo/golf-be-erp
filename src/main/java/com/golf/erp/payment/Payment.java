package com.golf.erp.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.payment. ERP 현장 결제는 pgProvider=ERP, pgTransactionId=ERP-{uuid}, 상태 PAID 로 남긴다
 * (ck_payment_paid: PAID 는 completed_at·pg_transaction_id 필수). 환불은 예약 앱과 같이 같은 행을 REFUNDED 로 바꾼다.
 */
@Entity
@Table(name = "payment")
public class Payment {
	public static final String ERP_PROVIDER = "ERP";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_id")
	private Long paymentId;
	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_type", nullable = false, length = 20)
	private PaymentType paymentType;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private PaymentStatus paymentStatus;
	@Column(name = "payment_method", length = 30)
	private String paymentMethod;
	@Column(name = "pg_provider", length = 50)
	private String pgProvider;
	@Column(name = "pg_transaction_id", length = 200)
	private String pgTransactionId;
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;
	@Column(name = "requested_at", nullable = false, updatable = false)
	private LocalDateTime requestedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Column(name = "refunded_at")
	private LocalDateTime refundedAt;
	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	protected Payment() {
	}

	public static Payment erpPaid(Long reservationId, String paymentMethod, BigDecimal amount, String transactionId,
			LocalDateTime now) {
		Payment p = new Payment();
		p.reservationId = reservationId;
		p.paymentType = PaymentType.PAYMENT;
		p.paymentStatus = PaymentStatus.PAID;
		p.paymentMethod = paymentMethod;
		p.pgProvider = ERP_PROVIDER;
		p.pgTransactionId = transactionId;
		p.amount = amount;
		p.requestedAt = now;
		p.completedAt = now;
		return p;
	}

	public boolean isErpPayment() {
		return ERP_PROVIDER.equals(pgProvider);
	}

	public void refund(LocalDateTime now) {
		this.paymentStatus = PaymentStatus.REFUNDED;
		this.refundedAt = now;
	}

	public Long getPaymentId() {
		return paymentId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public PaymentType getPaymentType() {
		return paymentType;
	}

	public PaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public String getPaymentMethod() {
		return paymentMethod;
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
