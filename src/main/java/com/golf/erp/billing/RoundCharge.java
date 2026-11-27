package com.golf.erp.billing;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * golf.round_charge — 라운드 요금. round_player_id 가 NULL 이면 팀 공통 요금.
 * amount = unit_price * quantity - discount_amount (DDL CHECK 와 같은 식). 취소는 지우지 않고 is_void 로 표시한다.
 */
@Entity
@Table(name = "round_charge")
public class RoundCharge {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_charge_id")
	private Long id;
	@Column(name = "round_id", nullable = false)
	private Long roundId;
	@Column(name = "round_player_id")
	private Long roundPlayerId;
	@Enumerated(EnumType.STRING)
	@Column(name = "charge_type", nullable = false, length = 20)
	private ChargeType chargeType;
	@Column(length = 200)
	private String description;
	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal unitPrice;
	@Column(name = "quantity", nullable = false)
	private Short quantity = 1;
	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;
	@Column(name = "is_void", nullable = false)
	private boolean voided;
	@Column(name = "voided_at")
	private LocalDateTime voidedAt;
	@Column(name = "void_reason", length = 200)
	private String voidReason;
	@Column(name = "created_by")
	private Long createdBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected RoundCharge() {
	}

	public RoundCharge(Long roundId, Long roundPlayerId, ChargeType chargeType, String description,
			BigDecimal unitPrice, short quantity, BigDecimal discountAmount, Long createdBy, LocalDateTime now) {
		BigDecimal price = money(unitPrice);
		BigDecimal discount = money(discountAmount == null ? BigDecimal.ZERO : discountAmount);
		if (price.signum() < 0 || discount.signum() < 0)
			throw new IllegalArgumentException("단가와 할인 금액은 0 이상이어야 합니다.");
		if (quantity < 1 || quantity > 100)
			throw new IllegalArgumentException("수량은 1~100 이어야 합니다.");
		BigDecimal gross = price.multiply(BigDecimal.valueOf(quantity));
		if (discount.compareTo(gross) > 0)
			throw new IllegalArgumentException("할인 금액이 요금(단가 × 수량)보다 클 수 없습니다.");
		this.roundId = roundId;
		this.roundPlayerId = roundPlayerId;
		this.chargeType = chargeType;
		this.description = description;
		this.unitPrice = price;
		this.quantity = quantity;
		this.discountAmount = discount;
		this.amount = money(gross.subtract(discount));
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	void voidCharge(String reason, LocalDateTime now) {
		if (voided)
			throw new ConflictException("이미 취소된 요금입니다.");
		this.voided = true;
		this.voidedAt = now;
		this.voidReason = reason;
	}

	/** 참가자를 라운드에서 뺄 때, 그 참가자에게 붙어 있던 (취소된) 요금을 팀 요금으로 돌린다. 설명에 이름을 남긴다. */
	void detachPlayer(String playerName) {
		this.roundPlayerId = null;
		String d = "[제외: " + playerName + "]" + (description == null ? "" : " " + description);
		this.description = d.length() > 200 ? d.substring(0, 200) : d;
	}

	static BigDecimal money(BigDecimal v) {
		return v.setScale(2, RoundingMode.HALF_UP);
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

	public ChargeType getChargeType() {
		return chargeType;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public Short getQuantity() {
		return quantity;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public boolean isVoided() {
		return voided;
	}

	public LocalDateTime getVoidedAt() {
		return voidedAt;
	}

	public String getVoidReason() {
		return voidReason;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
