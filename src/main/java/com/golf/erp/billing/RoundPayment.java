package com.golf.erp.billing;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** golf.round_payment — 라운드 수납(현장 결제·예약 선결제). 취소는 지우지 않고 CANCELLED 로 바꾼다. */
@Entity
@Table(name = "round_payment")
public class RoundPayment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_payment_id")
	private Long id;
	@Column(name = "round_id", nullable = false)
	private Long roundId;
	@Column(name = "round_player_id")
	private Long roundPlayerId;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 20)
	private PaymentMethod method;
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private PaymentStatus status = PaymentStatus.PAID;
	@Column(name = "approval_no", length = 100)
	private String approvalNo;
	@Column(length = 200)
	private String memo;
	@Column(name = "paid_at", nullable = false)
	private LocalDateTime paidAt;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "cancel_reason", length = 200)
	private String cancelReason;
	@Column(name = "received_by")
	private Long receivedBy;

	protected RoundPayment() {
	}

	public RoundPayment(Long roundId, Long roundPlayerId, PaymentMethod method, BigDecimal amount, String approvalNo,
			String memo, Long receivedBy, LocalDateTime now) {
		BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
		if (a.signum() <= 0)
			throw new IllegalArgumentException("수납 금액은 0보다 커야 합니다.");
		this.roundId = roundId;
		this.roundPlayerId = roundPlayerId;
		this.method = method;
		this.amount = a;
		this.approvalNo = approvalNo;
		this.memo = memo;
		this.receivedBy = receivedBy;
		this.paidAt = now;
	}

	void cancel(String reason, LocalDateTime now) {
		if (status == PaymentStatus.CANCELLED)
			throw new ConflictException("이미 취소된 수납입니다.");
		this.status = PaymentStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancelReason = reason;
	}

	/** 참가자를 라운드에서 뺄 때(취소된 수납만 남아 있는 경우) 팀 수납으로 돌린다. */
	void detachPlayer(String playerName) {
		this.roundPlayerId = null;
		String m = "[제외: " + playerName + "]" + (memo == null ? "" : " " + memo);
		this.memo = m.length() > 200 ? m.substring(0, 200) : m;
	}

	public boolean isPaid() {
		return status == PaymentStatus.PAID;
	}

	public Long getId() {
		return id;
	}

	public Long getRoundId() {
		return roundId;
	}

	public Long getRoundPlayerId() {
		return roundPlayerId;
	}

	public PaymentMethod getMethod() {
		return method;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public String getApprovalNo() {
		return approvalNo;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public Long getReceivedBy() {
		return receivedBy;
	}
}
