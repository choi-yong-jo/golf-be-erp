package com.golf.erp.accounting;

import com.golf.erp.accounting.AccountingDtos.ExpenseRequest;
import com.golf.erp.accounting.AccountingDtos.ExpenseResponse;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.security.AdminPrincipal;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 운영비. PENDING 에서 수정·삭제하고, 다른 직원이 승인·반려한다(본인 등록 건은 ADMIN 만 결재 가능).
 * 승인된 건만 지급(PAID) 처리한다.
 */
@Service
@Transactional
public class OperatingExpenseService {
	private final OperatingExpenseRepository repo;
	private final AccountRepository accounts;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public OperatingExpenseService(OperatingExpenseRepository repo, AccountRepository accounts, RefChecker refs,
			AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.accounts = accounts;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** from/to: 지출일 범위(포함). category: 정확히 일치. keyword: 거래처·내용 */
	@Transactional(readOnly = true)
	public PageResult<ExpenseResponse> list(Long golfCourseId, LocalDate from, LocalDate to, String category,
			ExpenseStatus status, Long accountId, String keyword, int page, int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		Specification<OperatingExpense> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.gte("expenseDate", from), Specs.lte("expenseDate", to),
				Specs.eq("category", category == null ? null : category.trim()), Specs.eq("status", status),
				Specs.eq("accountId", accountId), Specs.like(keyword, "vendorName", "description"));
		return PageResult.of(
				repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id")))),
				ExpenseResponse::of);
	}

	@Transactional(readOnly = true)
	public ExpenseResponse get(long id) {
		return ExpenseResponse.of(find(id));
	}

	public ExpenseResponse create(ExpenseRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		requireExpenseAccount(r.accountId());
		OperatingExpense e = new OperatingExpense(CurrentUser.idOrNull(), LocalDateTime.now(clock));
		apply(e, r);
		return ExpenseResponse.of(repo.save(e));
	}

	public ExpenseResponse update(long id, ExpenseRequest r) {
		OperatingExpense e = lock(id);
		e.requirePending("수정");
		if (!r.golfCourseId().equals(e.getGolfCourseId()))
			refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		if (r.accountId() != null && !r.accountId().equals(e.getAccountId()))
			requireExpenseAccount(r.accountId());
		apply(e, r);
		return ExpenseResponse.of(e);
	}

	public void delete(long id) {
		OperatingExpense e = lock(id);
		e.requirePending("삭제");
		Map<String, Object> details = new HashMap<>();
		details.put("category", e.getCategory());
		details.put("amount", e.getAmount());
		details.put("expenseDate", e.getExpenseDate().toString());
		repo.delete(e);
		audit.log("DELETE", "operating_expense", id, details);
	}

	public ExpenseResponse approve(long id) {
		OperatingExpense e = lock(id);
		AdminPrincipal approver = requireApprover(e);
		e.approve(approver.id());
		audit.log("APPROVE", "operating_expense", id, Map.of("amount", e.getAmount()));
		return ExpenseResponse.of(e);
	}

	public ExpenseResponse reject(long id, String reason) {
		OperatingExpense e = lock(id);
		AdminPrincipal approver = requireApprover(e);
		e.reject(approver.id());
		Map<String, Object> details = new HashMap<>();
		details.put("amount", e.getAmount());
		if (reason != null && !reason.isBlank())
			details.put("reason", reason.trim());
		audit.log("REJECT", "operating_expense", id, details);
		return ExpenseResponse.of(e);
	}

	public ExpenseResponse pay(long id, String paymentMethod) {
		OperatingExpense e = lock(id);
		e.pay(blankToNull(paymentMethod));
		Map<String, Object> details = new HashMap<>();
		details.put("amount", e.getAmount());
		details.put("paymentMethod", e.getPaymentMethod());
		audit.log("PAY", "operating_expense", id, details);
		return ExpenseResponse.of(e);
	}

	/** 결재자는 로그인한 직원이어야 하고, 본인이 등록한 건은 ADMIN 만 결재할 수 있다. */
	private static AdminPrincipal requireApprover(OperatingExpense e) {
		AdminPrincipal me = CurrentUser.require();
		e.requirePending("결재");
		if (e.getCreatedBy() != null && e.getCreatedBy() == me.id() && !me.isAdmin())
			throw new ConflictException("본인이 등록한 운영비는 결재할 수 없습니다. 다른 결재자에게 요청하세요.");
		return me;
	}

	/** 운영비 계정은 선택. 지정하면 활성 비용(EXPENSE) 계정이어야 한다. */
	private void requireExpenseAccount(Long accountId) {
		if (accountId == null)
			return;
		Account a = accounts.findById(accountId).orElseThrow(() -> NotFoundException.of("계정과목", accountId));
		if (!a.isActive())
			throw new ConflictException("비활성 계정입니다: " + a.getCode() + " " + a.getName());
		if (a.getType() != AccountType.EXPENSE)
			throw new IllegalArgumentException("운영비에는 비용(EXPENSE) 계정만 지정할 수 있습니다: " + a.getCode());
	}

	private void apply(OperatingExpense e, ExpenseRequest r) {
		e.update(r.golfCourseId(), r.accountId(), r.expenseDate(), r.category().trim(), blankToNull(r.vendorName()),
				blankToNull(r.description()), r.amount(), blankToNull(r.paymentMethod()));
	}

	private OperatingExpense lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("운영비", id));
	}

	private OperatingExpense find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("운영비", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
