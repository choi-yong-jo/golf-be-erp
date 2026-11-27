package com.golf.erp.billing;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.daily_settlement — 골프장·영업일(라운드 play_date) 단위 일 마감.
 * OPEN 동안은 언제든 다시 계산하고, CLOSED 로 바꾸는 순간의 금액이 스냅샷으로 남는다.
 */
@Entity
@Table(name = "daily_settlement")
public class DailySettlement {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "daily_settlement_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "business_date", nullable = false)
	private LocalDate businessDate;
	@Column(name = "round_count", nullable = false)
	private Integer roundCount = 0;
	@Column(name = "player_count", nullable = false)
	private Integer playerCount = 0;
	@Column(name = "charge_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal chargeTotal = BigDecimal.ZERO;
	@Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal discountTotal = BigDecimal.ZERO;
	@Column(name = "payment_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal paymentTotal = BigDecimal.ZERO;
	@Column(name = "unpaid_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal unpaidTotal = BigDecimal.ZERO;
	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_status", nullable = false, length = 20)
	private DailySettlementStatus status = DailySettlementStatus.OPEN;
	@Column(name = "closed_at")
	private LocalDateTime closedAt;
	@Column(name = "closed_by")
	private Long closedBy;
	@Column(length = 500)
	private String memo;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected DailySettlement() {
	}

	public DailySettlement(Long golfCourseId, LocalDate businessDate, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.businessDate = businessDate;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void applyTotals(int roundCount, int playerCount, BigDecimal chargeTotal, BigDecimal discountTotal,
			BigDecimal paymentTotal, LocalDateTime now) {
		requireOpen();
		this.roundCount = roundCount;
		this.playerCount = playerCount;
		this.chargeTotal = chargeTotal;
		this.discountTotal = discountTotal;
		this.paymentTotal = paymentTotal;
		this.unpaidTotal = chargeTotal.subtract(paymentTotal);
		this.updatedAt = now;
	}

	void changeMemo(String memo, LocalDateTime now) {
		this.memo = memo;
		this.updatedAt = now;
	}

	void close(Long closedBy, LocalDateTime now) {
		requireOpen();
		this.status = DailySettlementStatus.CLOSED;
		this.closedAt = now;
		this.closedBy = closedBy;
		this.updatedAt = now;
	}

	void reopen(LocalDateTime now) {
		if (status != DailySettlementStatus.CLOSED)
			throw new ConflictException("마감되지 않은 정산입니다.");
		this.status = DailySettlementStatus.OPEN;
		this.closedAt = null;
		this.closedBy = null;
		this.updatedAt = now;
	}

	void requireOpen() {
		if (status == DailySettlementStatus.CLOSED)
			throw new ConflictException(businessDate + " 영업일은 이미 마감되었습니다. 마감을 해제한 뒤 다시 시도하세요.");
	}

	public boolean isClosed() {
		return status == DailySettlementStatus.CLOSED;
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public LocalDate getBusinessDate() {
		return businessDate;
	}

	public Integer getRoundCount() {
		return roundCount;
	}

	public Integer getPlayerCount() {
		return playerCount;
	}

	public BigDecimal getChargeTotal() {
		return chargeTotal;
	}

	public BigDecimal getDiscountTotal() {
		return discountTotal;
	}

	public BigDecimal getPaymentTotal() {
		return paymentTotal;
	}

	public BigDecimal getUnpaidTotal() {
		return unpaidTotal;
	}

	public DailySettlementStatus getStatus() {
		return status;
	}

	public LocalDateTime getClosedAt() {
		return closedAt;
	}

	public Long getClosedBy() {
		return closedBy;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
