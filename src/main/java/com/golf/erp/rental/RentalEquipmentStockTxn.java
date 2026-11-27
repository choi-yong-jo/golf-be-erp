package com.golf.erp.rental;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.rental_equipment_stock_txn. 재고 변동 기록(추가만, 수정·삭제 없음). quantity 는 항상 양수(DB CHECK)이며
 * 방향은 유형이 정한다. ADJUSTMENT 의 증감 방향은 reason 앞머리에 기록한다.
 */
@Entity
@Table(name = "rental_equipment_stock_txn")
public class RentalEquipmentStockTxn {
	/** 렌탈 업무가 일으킨 변동의 reference_type */
	public static final String REF_RENTAL = "EQUIPMENT_RENTAL";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "stock_txn_id")
	private Long id;
	@Column(name = "rental_equipment_id", nullable = false, updatable = false)
	private Long rentalEquipmentId;
	@Column(name = "golf_course_id", nullable = false, updatable = false)
	private Long golfCourseId;
	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
	private StockTxnType type;
	@Column(name = "quantity", nullable = false, updatable = false)
	private int quantity;
	@Column(name = "reference_type", length = 30, updatable = false)
	private String referenceType;
	@Column(name = "reference_id", updatable = false)
	private Long referenceId;
	@Column(name = "reason", length = 500, updatable = false)
	private String reason;
	@Column(name = "actor_admin_user_id", updatable = false)
	private Long actorAdminUserId;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private LocalDateTime occurredAt;

	protected RentalEquipmentStockTxn() {
	}

	public RentalEquipmentStockTxn(Long rentalEquipmentId, Long golfCourseId, StockTxnType type, int quantity,
			String referenceType, Long referenceId, String reason, Long actorAdminUserId, LocalDateTime occurredAt) {
		this.rentalEquipmentId = rentalEquipmentId;
		this.golfCourseId = golfCourseId;
		this.type = type;
		this.quantity = quantity;
		this.referenceType = referenceType;
		this.referenceId = referenceId;
		this.reason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
		this.actorAdminUserId = actorAdminUserId;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public Long getRentalEquipmentId() {
		return rentalEquipmentId;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public StockTxnType getType() {
		return type;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public Long getReferenceId() {
		return referenceId;
	}

	public String getReason() {
		return reason;
	}

	public Long getActorAdminUserId() {
		return actorAdminUserId;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}
}
