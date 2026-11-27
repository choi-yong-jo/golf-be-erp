package com.golf.erp.hr;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.payroll. 직원별 월 급여 한 건(pay_period 는 해당 월 1일). net = base + overtime - deduction.
 * 금액은 DRAFT 에서만 바꿀 수 있고, DRAFT → APPROVED → PAID 순서로만 진행한다.
 */
@Entity
@Table(name = "payroll")
public class Payroll {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payroll_id")
	private Long id;
	@Column(name = "staff_id", nullable = false)
	private Long staffId;
	@Column(name = "pay_period", nullable = false)
	private LocalDate payPeriod;
	@Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal baseAmount = BigDecimal.ZERO;
	@Column(name = "overtime_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal overtimeAmount = BigDecimal.ZERO;
	@Column(name = "deduction_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal deductionAmount = BigDecimal.ZERO;
	@Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal netAmount = BigDecimal.ZERO;
	@Column(name = "paid_at")
	private LocalDateTime paidAt;
	@Enumerated(EnumType.STRING)
	@Column(name = "payroll_status", nullable = false, length = 20)
	private PayrollStatus status = PayrollStatus.DRAFT;
	@Column(name = "memo", length = 500)
	private String memo;

	protected Payroll() {
	}

	/** @param payPeriod 해당 월 1일로 정규화된 날짜 */
	public Payroll(Long staffId, LocalDate payPeriod) {
		this.staffId = staffId;
		this.payPeriod = payPeriod;
	}

	/** 금액·메모 변경(DRAFT 만). 실수령액이 음수가 되면 400 */
	public void changeAmounts(BigDecimal base, BigDecimal overtime, BigDecimal deduction, String memo) {
		if (status != PayrollStatus.DRAFT)
			throw new ConflictException("작성 중(DRAFT)인 급여만 수정할 수 있습니다. 현재: " + status);
		BigDecimal b = money(base);
		BigDecimal o = money(overtime);
		BigDecimal d = money(deduction);
		BigDecimal net = b.add(o).subtract(d);
		if (net.signum() < 0)
			throw new IllegalArgumentException("공제액이 지급액(기본급+초과근무수당)보다 클 수 없습니다.");
		this.baseAmount = b;
		this.overtimeAmount = o;
		this.deductionAmount = d;
		this.netAmount = net;
		this.memo = memo;
	}

	public void approve() {
		if (status != PayrollStatus.DRAFT)
			throw new ConflictException("작성 중(DRAFT)인 급여만 승인할 수 있습니다. 현재: " + status);
		this.status = PayrollStatus.APPROVED;
	}

	public void pay(LocalDateTime now) {
		if (status != PayrollStatus.APPROVED)
			throw new ConflictException("승인(APPROVED)된 급여만 지급 처리할 수 있습니다. 현재: " + status);
		this.status = PayrollStatus.PAID;
		this.paidAt = now;
	}

	private static BigDecimal money(BigDecimal v) {
		return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
	}

	public Long getId() {
		return id;
	}

	public Long getStaffId() {
		return staffId;
	}

	public LocalDate getPayPeriod() {
		return payPeriod;
	}

	public BigDecimal getBaseAmount() {
		return baseAmount;
	}

	public BigDecimal getOvertimeAmount() {
		return overtimeAmount;
	}

	public BigDecimal getDeductionAmount() {
		return deductionAmount;
	}

	public BigDecimal getNetAmount() {
		return netAmount;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public PayrollStatus getStatus() {
		return status;
	}

	public String getMemo() {
		return memo;
	}
}
