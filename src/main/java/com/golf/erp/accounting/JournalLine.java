package com.golf.erp.accounting;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** golf.journal_line. 전표 한 줄. 차변·대변 중 한쪽에만 금액이 있다(DDL CHECK). */
@Entity
@Table(name = "journal_line")
public class JournalLine {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "journal_line_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "journal_entry_id", nullable = false)
	private JournalEntry entry;
	@Column(name = "account_id", nullable = false)
	private Long accountId;
	@Column(name = "debit_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal debitAmount = BigDecimal.ZERO;
	@Column(name = "credit_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal creditAmount = BigDecimal.ZERO;
	@Column(name = "memo", length = 300)
	private String memo;

	protected JournalLine() {
	}

	/** 금액은 0 이상이고, 차변·대변 중 정확히 한쪽만 0 보다 커야 한다. */
	public JournalLine(Long accountId, BigDecimal debit, BigDecimal credit, String memo) {
		BigDecimal d = money(debit);
		BigDecimal c = money(credit);
		if (d.signum() < 0 || c.signum() < 0)
			throw new IllegalArgumentException("전표 금액은 0 이상이어야 합니다.");
		if (d.signum() > 0 == c.signum() > 0)
			throw new IllegalArgumentException("전표 한 줄에는 차변 또는 대변 중 한쪽에만 금액을 입력하세요.");
		this.accountId = accountId;
		this.debitAmount = d;
		this.creditAmount = c;
		this.memo = memo;
	}

	void attachTo(JournalEntry entry) {
		this.entry = entry;
	}

	private static BigDecimal money(BigDecimal v) {
		return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
	}

	public Long getId() {
		return id;
	}

	public Long getAccountId() {
		return accountId;
	}

	public BigDecimal getDebitAmount() {
		return debitAmount;
	}

	public BigDecimal getCreditAmount() {
		return creditAmount;
	}

	public String getMemo() {
		return memo;
	}
}
