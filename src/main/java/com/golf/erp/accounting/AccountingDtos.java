package com.golf.erp.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 회계(계정과목·전표·운영비) API 요청/응답 */
public final class AccountingDtos {
	/** NUMERIC(14,2) 상한 */
	static final String MAX_AMOUNT = "999999999999.99";

	private AccountingDtos() {
	}

	// ---- 계정과목 ----

	/** parentAccountId: 상위 계정(선택, 같은 유형이어야 함) */
	public record AccountRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 120) String name,
			@NotNull AccountType type, Long parentAccountId) {
	}

	public record AccountResponse(Long id, String code, String name, AccountType type, Long parentAccountId,
			boolean active) {

		static AccountResponse of(Account a) {
			return new AccountResponse(a.getId(), a.getCode(), a.getName(), a.getType(), a.getParentAccountId(),
					a.isActive());
		}
	}

	public record AccountNode(Long id, String code, String name, AccountType type, Long parentAccountId,
			boolean active, List<AccountNode> children) {
	}

	// ---- 전표 ----

	/** 차변(debit)·대변(credit) 중 한쪽에만 금액을 넣는다. 다른 쪽은 비우거나 0 */
	public record JournalLineRequest(@NotNull Long accountId,
			@PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal debitAmount,
			@PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal creditAmount, @Size(max = 300) String memo) {
	}

	/** sourceType/sourceId: 원천 업무(예: OPERATING_EXPENSE, 12). 수기 전표면 비운다 */
	public record JournalEntryRequest(@NotNull Long golfCourseId, @NotNull LocalDate businessDate,
			@Size(max = 500) String description, @Size(max = 30) String sourceType, Long sourceId,
			@NotEmpty @Size(max = 200) List<@Valid @NotNull JournalLineRequest> lines) {
	}

	public record VoidRequest(@Size(max = 500) String reason) {
	}

	public record JournalLineResponse(Long id, Long accountId, String accountCode, String accountName,
			BigDecimal debitAmount, BigDecimal creditAmount, String memo) {

		static JournalLineResponse of(JournalLine l, Account a) {
			return new JournalLineResponse(l.getId(), l.getAccountId(), a == null ? null : a.getCode(),
					a == null ? null : a.getName(), l.getDebitAmount(), l.getCreditAmount(), l.getMemo());
		}
	}

	/** 목록용(줄 없이 합계만) */
	public record JournalEntrySummary(Long id, Long golfCourseId, String entryNo, LocalDate businessDate,
			String description, String sourceType, Long sourceId, JournalStatus status, BigDecimal totalDebit,
			BigDecimal totalCredit, Long createdBy, LocalDateTime postedAt, LocalDateTime createdAt) {

		static JournalEntrySummary of(JournalEntry e, BigDecimal totalDebit, BigDecimal totalCredit) {
			return new JournalEntrySummary(e.getId(), e.getGolfCourseId(), e.getEntryNo(), e.getBusinessDate(),
					e.getDescription(), e.getSourceType(), e.getSourceId(), e.getStatus(), totalDebit, totalCredit,
					e.getCreatedBy(), e.getPostedAt(), e.getCreatedAt());
		}
	}

	public record JournalEntryResponse(Long id, Long golfCourseId, String entryNo, LocalDate businessDate,
			String description, String sourceType, Long sourceId, JournalStatus status, BigDecimal totalDebit,
			BigDecimal totalCredit, boolean balanced, Long createdBy, LocalDateTime postedAt,
			LocalDateTime createdAt, List<JournalLineResponse> lines) {

		static JournalEntryResponse of(JournalEntry e, Map<Long, Account> accounts) {
			BigDecimal d = e.totalDebit();
			BigDecimal c = e.totalCredit();
			return new JournalEntryResponse(e.getId(), e.getGolfCourseId(), e.getEntryNo(), e.getBusinessDate(),
					e.getDescription(), e.getSourceType(), e.getSourceId(), e.getStatus(), d, c,
					d.signum() > 0 && d.compareTo(c) == 0, e.getCreatedBy(), e.getPostedAt(), e.getCreatedAt(),
					e.getLines().stream().map(l -> JournalLineResponse.of(l, accounts.get(l.getAccountId())))
							.toList());
		}
	}

	/** balance: 계정 유형의 정상 잔액 방향 기준(자산·비용 = 차변 - 대변, 그 외 = 대변 - 차변) */
	public record TrialBalanceRow(Long accountId, String accountCode, String accountName, AccountType accountType,
			BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal balance) {
	}

	public record TrialBalanceResponse(Long golfCourseId, LocalDate from, LocalDate to, List<TrialBalanceRow> rows,
			BigDecimal debitTotal, BigDecimal creditTotal, boolean balanced) {
	}

	// ---- 운영비 ----

	/** accountId: 비용(EXPENSE) 계정(선택). paymentMethod: 예정 결제 수단(예: CARD, CASH, TRANSFER) */
	public record ExpenseRequest(@NotNull Long golfCourseId, Long accountId, @NotNull LocalDate expenseDate,
			@NotBlank @Size(max = 60) String category, @Size(max = 150) String vendorName,
			@Size(max = 500) String description, @NotNull @Positive @DecimalMax(MAX_AMOUNT) BigDecimal amount,
			@Size(max = 20) String paymentMethod) {
	}

	public record ExpenseRejectRequest(@Size(max = 500) String reason) {
	}

	/** paymentMethod 를 비우면 등록 시 값 유지 */
	public record ExpensePayRequest(@Size(max = 20) String paymentMethod) {
	}

	public record ExpenseResponse(Long id, Long golfCourseId, Long accountId, LocalDate expenseDate, String category,
			String vendorName, String description, BigDecimal amount, String paymentMethod, ExpenseStatus status,
			Long createdBy, Long approvedBy, LocalDateTime createdAt) {

		static ExpenseResponse of(OperatingExpense e) {
			return new ExpenseResponse(e.getId(), e.getGolfCourseId(), e.getAccountId(), e.getExpenseDate(),
					e.getCategory(), e.getVendorName(), e.getDescription(), e.getAmount(), e.getPaymentMethod(),
					e.getStatus(), e.getCreatedBy(), e.getApprovedBy(), e.getCreatedAt());
		}
	}
}
