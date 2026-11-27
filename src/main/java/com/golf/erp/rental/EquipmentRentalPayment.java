package com.golf.erp.rental;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * golf.equipment_rental_payment. 대여료·보증금·파손료 수납과 환불(REFUND, 금액은 양수로 기록).
 * 잘못 입력한 결제는 취소(CANCELLED)하고 합계에서 뺀다.
 */
@Entity
@Table(name = "equipment_rental_payment")
public class EquipmentRentalPayment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "rental_payment_id")
	private Long id;
	@Column(name = "equipment_rental_id", nullable = false, updatable = false)
	private Long equipmentRentalId;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_type", nullable = false, length = 20, updatable = false)
	private RentalPaymentType type;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false, length = 20, updatable = false)
	private RentalPaymentMethod method;
	@Column(name = "amount", nullable = false, precision = 14, scale = 2, updatable = false)
	private BigDecimal amount;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private RentalPaymentStatus status = RentalPaymentStatus.PAID;
	@Column(name = "approval_no", length = 100, updatable = false)
	private String approvalNo;
	@Column(name = "paid_at", nullable = false, updatable = false)
	private LocalDateTime paidAt;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "received_by", updatable = false)
	private Long receivedBy;

	protected EquipmentRentalPayment() {
	}

	public EquipmentRentalPayment(Long equipmentRentalId, RentalPaymentType type, RentalPaymentMethod method,
			BigDecimal amount, String approvalNo, Long receivedBy, LocalDateTime paidAt) {
		if (amount == null || amount.signum() <= 0)
			throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다.");
		this.equipmentRentalId = equipmentRentalId;
		this.type = type;
		this.method = method;
		this.amount = amount.setScale(2, RoundingMode.HALF_UP);
		this.approvalNo = approvalNo;
		this.receivedBy = receivedBy;
		this.paidAt = paidAt;
		this.status = RentalPaymentStatus.PAID;
	}

	void cancel(LocalDateTime now) {
		if (status != RentalPaymentStatus.PAID)
			throw new ConflictException("이미 취소된 결제입니다.");
		this.status = RentalPaymentStatus.CANCELLED;
		this.cancelledAt = now;
	}

	public boolean isPaid() {
		return status == RentalPaymentStatus.PAID;
	}

	public Long getId() {
		return id;
	}

	public Long getEquipmentRentalId() {
		return equipmentRentalId;
	}

	public RentalPaymentType getType() {
		return type;
	}

	public RentalPaymentMethod getMethod() {
		return method;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public RentalPaymentStatus getStatus() {
		return status;
	}

	public String getApprovalNo() {
		return approvalNo;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public Long getReceivedBy() {
		return receivedBy;
	}
}
