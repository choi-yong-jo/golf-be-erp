package com.golf.erp.settlement;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** golf.settlement. 일별 정산. settlementStatus: OPEN(재계산 가능) / CLOSED(마감) */
@Entity
@Table(name = "settlement")
public class Settlement {
	public static final String OPEN = "OPEN";
	public static final String CLOSED = "CLOSED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "settlement_id")
	private Long settlementId;
	@Column(name = "settlement_date", nullable = false)
	private LocalDate settlementDate;
	@Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal grossAmount;
	@Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal refundAmount;
	@Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal netAmount;
	@Column(name = "settlement_status", nullable = false, length = 20)
	private String settlementStatus = OPEN;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Settlement() {
	}

	public Settlement(LocalDate settlementDate, LocalDateTime now) {
		this.settlementDate = settlementDate;
		this.createdAt = now;
	}

	public void apply(BigDecimal gross, BigDecimal refund) {
		this.grossAmount = gross;
		this.refundAmount = refund;
		this.netAmount = gross.subtract(refund);
	}

	public boolean isClosed() {
		return CLOSED.equals(settlementStatus);
	}

	public void close() {
		this.settlementStatus = CLOSED;
	}

	public void reopen() {
		this.settlementStatus = OPEN;
	}

	public Long getSettlementId() {
		return settlementId;
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

	public String getSettlementStatus() {
		return settlementStatus;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
