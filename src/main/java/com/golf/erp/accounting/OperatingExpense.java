package com.golf.erp.accounting;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.operating_expense. 운영비 지출 결의. PENDING 에서만 수정·삭제할 수 있고, 승인(APPROVED) 후 지급(PAID)한다.
 * approved_by 에는 승인 또는 반려한 결재자를 남긴다.
 */
@Entity
@Table(name = "operating_expense")
public class OperatingExpense {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "operating_expense_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "account_id")
	private Long accountId;
	@Column(name = "expense_date", nullable = false)
	private LocalDate expenseDate;
	@Column(name = "category", nullable = false, length = 60)
	private String category;
	@Column(name = "vendor_name", length = 150)
	private String vendorName;
	@Column(name = "description", length = 500)
	private String description;
	@Column(name = "amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;
	@Column(name = "payment_method", length = 20)
	private String paymentMethod;
	@Enumerated(EnumType.STRING)
	@Column(name = "approval_status", nullable = false, length = 20)
	private ExpenseStatus status = ExpenseStatus.PENDING;
	@Column(name = "created_by")
	private Long createdBy;
	@Column(name = "approved_by")
	private Long approvedBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected OperatingExpense() {
	}

	public OperatingExpense(Long createdBy, LocalDateTime now) {
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public void update(Long golfCourseId, Long accountId, LocalDate expenseDate, String category, String vendorName,
			String description, BigDecimal amount, String paymentMethod) {
		requirePending("수정");
		BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
		if (a.signum() <= 0)
			throw new IllegalArgumentException("금액은 0 보다 커야 합니다.");
		this.golfCourseId = golfCourseId;
		this.accountId = accountId;
		this.expenseDate = expenseDate;
		this.category = category;
		this.vendorName = vendorName;
		this.description = description;
		this.amount = a;
		this.paymentMethod = paymentMethod;
	}

	public void approve(Long approverId) {
		requirePending("승인");
		this.status = ExpenseStatus.APPROVED;
		this.approvedBy = approverId;
	}

	public void reject(Long approverId) {
		requirePending("반려");
		this.status = ExpenseStatus.REJECTED;
		this.approvedBy = approverId;
	}

	/** @param paymentMethod 비우면 등록 시 값 유지 */
	public void pay(String paymentMethod) {
		if (status != ExpenseStatus.APPROVED)
			throw new ConflictException("승인(APPROVED)된 운영비만 지급 처리할 수 있습니다. 현재: " + status);
		if (paymentMethod != null)
			this.paymentMethod = paymentMethod;
		this.status = ExpenseStatus.PAID;
	}

	public void requirePending(String action) {
		if (status != ExpenseStatus.PENDING)
			throw new ConflictException("결재 대기(PENDING) 중인 운영비만 " + action + "할 수 있습니다. 현재: " + status);
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public Long getAccountId() {
		return accountId;
	}

	public LocalDate getExpenseDate() {
		return expenseDate;
	}

	public String getCategory() {
		return category;
	}

	public String getVendorName() {
		return vendorName;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public ExpenseStatus getStatus() {
		return status;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getApprovedBy() {
		return approvedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
