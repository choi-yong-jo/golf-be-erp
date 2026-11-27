package com.golf.erp.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * golf.inventory_item. 현재고 컬럼은 없고 inventory_transaction 합계로 계산한다({@link InventoryTransactionType}).
 * 재고를 줄이는 처리는 이 행을 PESSIMISTIC_WRITE 로 잠근 뒤 현재고를 확인한다.
 */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "inventory_item_id")
	private Long id;
	@Column(name = "item_code", nullable = false, length = 40)
	private String itemCode;
	@Column(name = "item_name", nullable = false, length = 150)
	private String itemName;
	@Column(name = "unit_name", nullable = false, length = 30)
	private String unitName = "EA";
	@Column(name = "reorder_level", nullable = false, precision = 12, scale = 3)
	private BigDecimal reorderLevel = BigDecimal.ZERO;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected InventoryItem() {
	}

	public InventoryItem(String itemCode) {
		this.itemCode = itemCode;
	}

	public void update(String itemCode, String itemName, String unitName, BigDecimal reorderLevel) {
		this.itemCode = itemCode;
		this.itemName = itemName;
		this.unitName = unitName;
		this.reorderLevel = reorderLevel;
	}

	public void changeActive(boolean active) {
		this.active = active;
	}

	/** 재주문 필요: 재주문 기준이 0 보다 크고 현재고가 기준 이하 */
	public boolean needsReorder(BigDecimal stock) {
		return reorderLevel.signum() > 0 && stock.compareTo(reorderLevel) <= 0;
	}

	public Long getId() {
		return id;
	}

	public String getItemCode() {
		return itemCode;
	}

	public String getItemName() {
		return itemName;
	}

	public String getUnitName() {
		return unitName;
	}

	public BigDecimal getReorderLevel() {
		return reorderLevel;
	}

	public boolean isActive() {
		return active;
	}
}
