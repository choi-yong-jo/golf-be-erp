package com.golf.erp.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** golf.daily_settlement_item — 일 마감의 요금 종류별·수납 수단별 합계. 다시 계산할 때마다 통째로 바꾼다. */
@Entity
@Table(name = "daily_settlement_item")
public class DailySettlementItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "daily_settlement_item_id")
	private Long id;
	@Column(name = "daily_settlement_id", nullable = false)
	private Long dailySettlementId;
	@Enumerated(EnumType.STRING)
	@Column(name = "item_type", nullable = false, length = 10)
	private SettlementItemType itemType;
	@Column(name = "item_code", nullable = false, length = 20)
	private String itemCode;
	@Column(name = "item_count", nullable = false)
	private Integer itemCount = 0;
	@Column(name = "amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal amount = BigDecimal.ZERO;

	protected DailySettlementItem() {
	}

	public DailySettlementItem(Long dailySettlementId, SettlementItemType itemType, String itemCode, int itemCount,
			BigDecimal amount) {
		this.dailySettlementId = dailySettlementId;
		this.itemType = itemType;
		this.itemCode = itemCode;
		this.itemCount = itemCount;
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public Long getDailySettlementId() {
		return dailySettlementId;
	}

	public SettlementItemType getItemType() {
		return itemType;
	}

	public String getItemCode() {
		return itemCode;
	}

	public Integer getItemCount() {
		return itemCount;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}
