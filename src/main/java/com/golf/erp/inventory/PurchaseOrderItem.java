package com.golf.erp.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** golf.purchase_order_item. 발주서 한 줄(같은 발주서에 같은 품목은 한 줄만: uq_po_item). */
@Entity
@Table(name = "purchase_order_item")
public class PurchaseOrderItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "purchase_order_item_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "purchase_order_id", nullable = false)
	private PurchaseOrder purchaseOrder;
	@Column(name = "inventory_item_id", nullable = false)
	private Long inventoryItemId;
	@Column(name = "ordered_quantity", nullable = false, precision = 12, scale = 3)
	private BigDecimal orderedQuantity;
	@Column(name = "received_quantity", nullable = false, precision = 12, scale = 3)
	private BigDecimal receivedQuantity = BigDecimal.ZERO;
	@Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitCost;

	protected PurchaseOrderItem() {
	}

	PurchaseOrderItem(PurchaseOrder purchaseOrder, Long inventoryItemId, BigDecimal orderedQuantity,
			BigDecimal unitCost) {
		this.purchaseOrder = purchaseOrder;
		this.inventoryItemId = inventoryItemId;
		change(orderedQuantity, unitCost);
	}

	void change(BigDecimal orderedQuantity, BigDecimal unitCost) {
		this.orderedQuantity = orderedQuantity;
		this.unitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
	}

	void receive(BigDecimal quantity) {
		BigDecimal after = receivedQuantity.add(quantity);
		if (after.compareTo(orderedQuantity) > 0)
			throw new IllegalArgumentException("입고 수량이 남은 발주 수량(" + remainingQuantity().toPlainString() + ")을 넘습니다.");
		receivedQuantity = after;
	}

	public BigDecimal remainingQuantity() {
		return orderedQuantity.subtract(receivedQuantity);
	}

	public boolean fullyReceived() {
		return receivedQuantity.compareTo(orderedQuantity) >= 0;
	}

	/** ordered_quantity × unit_cost (원 단위 소수 둘째 자리 반올림) */
	public BigDecimal amount() {
		return orderedQuantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
	}

	public Long getId() {
		return id;
	}

	public Long getInventoryItemId() {
		return inventoryItemId;
	}

	public BigDecimal getOrderedQuantity() {
		return orderedQuantity;
	}

	public BigDecimal getReceivedQuantity() {
		return receivedQuantity;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}
}
