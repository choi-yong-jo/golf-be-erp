package com.golf.erp.settlement;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.settlement. 예약 결제(golf.payment)의 일별 합계. gross = 그날 완료된 결제, refund = 그날 환불된 금액, net = gross - refund.
 * 마감(CLOSED)하면 해제(reopen) 전까지 재계산하지 않는다.
 */
@Entity
@Table(name = "settlement")
public class Settlement {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "settlement_id")
	private Long id;
	@Column(name = "settlement_date", nullable = false, updatable = false)
	private LocalDate settlementDate;
	@Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal grossAmount = BigDecimal.ZERO;
	@Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal refundAmount = BigDecimal.ZERO;
	@Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal netAmount = BigDecimal.ZERO;
	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_status", nullable = false, length = 20)
	private SettlementStatus status = SettlementStatus.OPEN;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Settlement() {
	}

	public Settlement(LocalDate settlementDate, LocalDateTime now) {
		this.settlementDate = settlementDate;
		this.createdAt = now;
	}

	/** 집계 금액 반영. 마감된 정산은 바꿀 수 없다. */
	public void apply(BigDecimal gross, BigDecimal refund) {
		if (isClosed())
			throw new ConflictException("마감된 정산입니다. 재계산하려면 먼저 마감을 해제하세요.");
		this.grossAmount = gross.setScale(2, RoundingMode.HALF_UP);
		this.refundAmount = refund.setScale(2, RoundingMode.HALF_UP);
		this.netAmount = grossAmount.subtract(refundAmount);
	}

	public boolean isClosed() {
		return status == SettlementStatus.CLOSED;
	}

	public void close() {
		if (isClosed())
			throw new ConflictException("이미 마감된 정산입니다: " + settlementDate);
		this.status = SettlementStatus.CLOSED;
	}

	public void reopen() {
		if (!isClosed())
			throw new ConflictException("마감되지 않은 정산입니다: " + settlementDate);
		this.status = SettlementStatus.OPEN;
	}

	public Long getId() {
		return id;
	}

	public LocalDate getSettlementDate() {
		return settlementDate;
	}

	public BigDecimal getGrossAmount() {
		return grossAmount;
	}

	public BigDecimal getRefundAmount() {
		return refundAmount;
	}

	public BigDecimal getNetAmount() {
		return netAmount;
	}

	public SettlementStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
