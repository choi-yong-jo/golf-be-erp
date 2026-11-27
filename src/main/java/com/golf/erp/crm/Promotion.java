package com.golf.erp.crm;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * golf.promotion. 할인 코드. 유효 기간은 [starts_at, ends_at) (ends_at 미포함).
 * 할인액: AMOUNT 는 min(할인값, 기준 금액), PERCENT 는 기준 금액 × 할인율을 원 단위 절사.
 */
@Entity
@Table(name = "promotion")
public class Promotion {
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "promotion_id")
	private Long id;
	@Column(name = "promotion_code", nullable = false, length = 40)
	private String code;
	@Column(name = "promotion_name", nullable = false, length = 150)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(name = "discount_type", nullable = false, length = 20)
	private DiscountType discountType;
	@Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountValue;
	@Column(name = "starts_at", nullable = false)
	private LocalDateTime startsAt;
	@Column(name = "ends_at", nullable = false)
	private LocalDateTime endsAt;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected Promotion() {
	}

	public Promotion(String code) {
		this.code = code;
	}

	public void update(String code, String name, DiscountType discountType, BigDecimal discountValue,
			LocalDateTime startsAt, LocalDateTime endsAt) {
		if (discountValue.signum() <= 0)
			throw new IllegalArgumentException("할인 값은 0보다 커야 합니다.");
		if (discountType == DiscountType.PERCENT && discountValue.compareTo(HUNDRED) > 0)
			throw new IllegalArgumentException("할인율은 100% 이하여야 합니다.");
		if (!endsAt.isAfter(startsAt))
			throw new IllegalArgumentException("종료 시각은 시작 시각보다 뒤여야 합니다.");
		this.code = code;
		this.name = name;
		this.discountType = discountType;
		this.discountValue = discountValue.setScale(2, RoundingMode.HALF_UP);
		this.startsAt = startsAt;
		this.endsAt = endsAt;
	}

	public void changeActive(boolean active) {
		this.active = active;
	}

	/** 지금 쓸 수 있는지(활성 + 기간 안). 쓸 수 없으면 사유, 쓸 수 있으면 null */
	public String unavailableReason(LocalDateTime at) {
		if (!active)
			return "사용 중지된 프로모션입니다.";
		if (at.isBefore(startsAt))
			return "아직 시작되지 않은 프로모션입니다.";
		if (!at.isBefore(endsAt))
			return "기간이 끝난 프로모션입니다.";
		return null;
	}

	/** 기준 금액에 대한 할인액(기준 금액을 넘지 않음, 소수 둘째 자리 표기) */
	public BigDecimal discountFor(BigDecimal baseAmount) {
		BigDecimal d = discountType == DiscountType.AMOUNT ? discountValue.min(baseAmount)
				: baseAmount.multiply(discountValue).divide(HUNDRED, 0, RoundingMode.DOWN);
		return d.min(baseAmount).setScale(2, RoundingMode.DOWN);
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public DiscountType getDiscountType() {
		return discountType;
	}

	public BigDecimal getDiscountValue() {
		return discountValue;
	}

	public LocalDateTime getStartsAt() {
		return startsAt;
	}

	public LocalDateTime getEndsAt() {
		return endsAt;
	}

	public boolean isActive() {
		return active;
	}
}
