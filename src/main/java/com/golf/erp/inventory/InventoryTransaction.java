package com.golf.erp.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.inventory_transaction. 재고 수불 원장. 한 번 기록하면 바꾸거나 지우지 않는다(정정은 반대 방향 거래로).
 * 그래서 변경 메서드가 없고 모든 컬럼이 updatable = false 다.
 */
@Entity
@Table(name = "inventory_transaction")
public class InventoryTransaction {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "inventory_transaction_id")
	private Long id;
	@Column(name = "inventory_item_id", nullable = false, updatable = false)
	private Long inventoryItemId;
	@Column(name = "facility_id", updatable = false)
	private Long facilityId;
	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
	private InventoryTransactionType type;
	@Column(name = "quantity", nullable = false, precision = 12, scale = 3, updatable = false)
	private BigDecimal quantity;
	@Column(name = "unit_cost", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal unitCost = BigDecimal.ZERO;
	@Column(name = "reference_no", length = 60, updatable = false)
	private String referenceNo;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private LocalDateTime occurredAt;
	@Column(name = "actor_admin_user_id", updatable = false)
	private Long actorAdminUserId;
	@Column(length = 500, updatable = false)
	private String memo;

	protected InventoryTransaction() {
	}

	public InventoryTransaction(Long inventoryItemId, Long facilityId, InventoryTransactionType type,
			BigDecimal quantity, BigDecimal unitCost, String referenceNo, LocalDateTime occurredAt,
			Long actorAdminUserId, String memo) {
		if (quantity == null || quantity.signum() <= 0)
			throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
		this.inventoryItemId = inventoryItemId;
		this.facilityId = facilityId;
		this.type = type;
		this.quantity = quantity;
		this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
		this.referenceNo = referenceNo;
		this.occurredAt = occurredAt;
		this.actorAdminUserId = actorAdminUserId;
		this.memo = memo;
	}

	/** 현재고에 반영되는 부호 있는 수량 */
	public BigDecimal signedQuantity() {
		return type.increases() ? quantity : quantity.negate();
	}

	/** 실사 조정 거래인지(증가: ADJUSTMENT, 감소: memo 표시가 붙은 ISSUE) */
	public boolean isAdjustment() {
		return type == InventoryTransactionType.ADJUSTMENT || (type == InventoryTransactionType.ISSUE && memo != null
				&& memo.startsWith(InventoryTransactionType.ADJUSTMENT_MEMO_PREFIX));
	}

	public Long getId() {
		return id;
	}

	public Long getInventoryItemId() {
		return inventoryItemId;
	}

	public Long getFacilityId() {
		return facilityId;
	}

	public InventoryTransactionType getType() {
		return type;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public String getReferenceNo() {
		return referenceNo;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public Long getActorAdminUserId() {
		return actorAdminUserId;
	}

	public String getMemo() {
		return memo;
	}
}
