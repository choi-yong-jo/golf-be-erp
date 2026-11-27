package com.golf.erp.crm;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** golf.promotion_redemption. 프로모션 사용 이력(할인액은 사용 시점 계산값 스냅샷). 수정·삭제하지 않는다. */
@Entity
@Table(name = "promotion_redemption")
public class PromotionRedemption {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "promotion_redemption_id")
	private Long id;
	@Column(name = "promotion_id", nullable = false, updatable = false)
	private Long promotionId;
	@Column(name = "member_id", nullable = false, updatable = false)
	private Long memberId;
	@Column(name = "reservation_id", updatable = false)
	private Long reservationId;
	@Column(name = "redeemed_at", nullable = false, updatable = false)
	private LocalDateTime redeemedAt;
	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal discountAmount;

	protected PromotionRedemption() {
	}

	public PromotionRedemption(Long promotionId, Long memberId, Long reservationId, LocalDateTime redeemedAt,
			BigDecimal discountAmount) {
		this.promotionId = promotionId;
		this.memberId = memberId;
		this.reservationId = reservationId;
		this.redeemedAt = redeemedAt;
		this.discountAmount = discountAmount;
	}

	public Long getId() {
		return id;
	}

	public Long getPromotionId() {
		return promotionId;
	}

	public Long getMemberId() {
		return memberId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public LocalDateTime getRedeemedAt() {
		return redeemedAt;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}
}
