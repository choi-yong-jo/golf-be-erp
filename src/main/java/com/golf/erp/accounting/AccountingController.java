package com.golf.erp.accounting;

import com.golf.erp.accounting.AccountingDtos.*;
import com.golf.erp.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "회계")
@RestController
@RequestMapping("/api")
public class AccountingController {
	private final AccountService accounts;
	private final JournalEntryService journals;
	private final OperatingExpenseService expenses;

	public AccountingController(AccountService accounts, JournalEntryService journals,
			OperatingExpenseService expenses) {
		this.accounts = accounts;
		this.journals = journals;
		this.expenses = expenses;
	}

	// ---- 계정과목 ----

	@Operation(summary = "계정과목 목록", description = "코드순 전체. keyword: 코드·이름")
	@GetMapping("/accounts")
	public List<AccountResponse> accountList(@RequestParam(required = false) AccountType type,
			@RequestParam(required = false) Boolean active, @RequestParam(required = false) String keyword) {
		return accounts.list(type, active, keyword);
	}

	@Operation(summary = "계정과목 트리", description = "activeOnly=true 면 비활성 계정(과 그 하위)을 뺀다")
	@GetMapping("/accounts/tree")
	public List<AccountNode> accountTree(@RequestParam(required = false) AccountType type,
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return accounts.tree(type, activeOnly);
	}

	@GetMapping("/accounts/{id}")
	public AccountResponse account(@PathVariable long id) {
		return accounts.get(id);
	}

	@Operation(summary = "계정과목 등록", description = "코드 중복이면 409. 상위 계정은 활성이고 같은 유형이어야 한다")
	@PostMapping("/accounts")
	@ResponseStatus(HttpStatus.CREATED)
	public AccountResponse createAccount(@Valid @RequestBody AccountRequest r) {
		return accounts.create(r);
	}

	@Operation(summary = "계정과목 수정", description = "사용된 계정·하위 계정이 있는 계정은 유형 변경 불가(409). 순환 상위 지정 불가(400)")
	@PutMapping("/accounts/{id}")
	public AccountResponse updateAccount(@PathVariable long id, @Valid @RequestBody AccountRequest r) {
		return accounts.update(id, r);
	}

	@Operation(summary = "계정과목 비활성화", description = "활성 하위 계정이 있으면 409")
	@PostMapping("/accounts/{id}/deactivate")
	public AccountResponse deactivateAccount(@PathVariable long id) {
		return accounts.deactivate(id);
	}

	@Operation(summary = "계정과목 활성화", description = "상위 계정이 비활성이면 409")
	@PostMapping("/accounts/{id}/activate")
	public AccountResponse activateAccount(@PathVariable long id) {
		return accounts.activate(id);
	}

	@Operation(summary = "계정과목 삭제", description = "사용 이력·하위 계정이 없는 계정만. 그 외는 비활성화")
	@DeleteMapping("/accounts/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAccount(@PathVariable long id) {
		accounts.delete(id);
	}

	// ---- 전표 ----

	@Operation(summary = "전표 목록", description = "from/to: 회계일 범위(포함). keyword: 전표 번호·적요. 줄 없이 차변·대변 합계만")
	@GetMapping("/journal-entries")
	public PageResult<JournalEntrySummary> journalList(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) JournalStatus status, @RequestParam(required = false) String sourceType,
			@RequestParam(required = false) Long sourceId, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return journals.list(golfCourseId, from, to, status, sourceType, sourceId, keyword, page, size);
	}

	@Operation(summary = "시산표", description = "전기(POSTED) 전표만 계정별 차변·대변 합계와 잔액. 모든 조건 선택")
	@GetMapping("/journal-entries/trial-balance")
	public TrialBalanceResponse trialBalance(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return journals.trialBalance(golfCourseId, from, to);
	}

	@GetMapping("/journal-entries/{id}")
	public JournalEntryResponse journal(@PathVariable long id) {
		return journals.get(id);
	}

	@Operation(summary = "전표 작성", description = "DRAFT 로 저장. 줄마다 차변·대변 중 한쪽만 금액 입력")
	@PostMapping("/journal-entries")
	@ResponseStatus(HttpStatus.CREATED)
	public JournalEntryResponse createJournal(@Valid @RequestBody JournalEntryRequest r) {
		return journals.create(r);
	}

	@Operation(summary = "전표 수정", description = "DRAFT 만. 줄은 요청 내용으로 모두 교체")
	@PutMapping("/journal-entries/{id}")
	public JournalEntryResponse updateJournal(@PathVariable long id, @Valid @RequestBody JournalEntryRequest r) {
		return journals.update(id, r);
	}

	@Operation(summary = "전표 전기", description = "DRAFT → POSTED. 두 줄 이상, 차변 합계 = 대변 합계 > 0, 모든 계정 활성")
	@PostMapping("/journal-entries/{id}/post")
	public JournalEntryResponse postJournal(@PathVariable long id) {
		return journals.post(id);
	}

	@Operation(summary = "전표 무효", description = "POSTED → VOID (시산표에서 빠진다)")
	@PostMapping("/journal-entries/{id}/void")
	public JournalEntryResponse voidJournal(@PathVariable long id, @Valid @RequestBody(required = false) VoidRequest r) {
		return journals.voidEntry(id, r == null ? null : r.reason());
	}

	@Operation(summary = "전표 삭제", description = "DRAFT 만. 전기된 전표는 무효 처리")
	@DeleteMapping("/journal-entries/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteJournal(@PathVariable long id) {
		journals.delete(id);
	}

	// ---- 운영비 ----

	@Operation(summary = "운영비 목록", description = "from/to: 지출일 범위(포함). category: 정확히 일치. keyword: 거래처·내용")
	@GetMapping("/operating-expenses")
	public PageResult<ExpenseResponse> expenseList(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String category, @RequestParam(required = false) ExpenseStatus status,
			@RequestParam(required = false) Long accountId, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return expenses.list(golfCourseId, from, to, category, status, accountId, keyword, page, size);
	}

	@GetMapping("/operating-expenses/{id}")
	public ExpenseResponse expense(@PathVariable long id) {
		return expenses.get(id);
	}

	@Operation(summary = "운영비 등록", description = "PENDING 으로 저장. 계정은 선택(활성 비용 계정)")
	@PostMapping("/operating-expenses")
	@ResponseStatus(HttpStatus.CREATED)
	public ExpenseResponse createExpense(@Valid @RequestBody ExpenseRequest r) {
		return expenses.create(r);
	}

	@Operation(summary = "운영비 수정", description = "PENDING 만")
	@PutMapping("/operating-expenses/{id}")
	public ExpenseResponse updateExpense(@PathVariable long id, @Valid @RequestBody ExpenseRequest r) {
		return expenses.update(id, r);
	}

	@Operation(summary = "운영비 삭제", description = "PENDING 만")
	@DeleteMapping("/operating-expenses/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteExpense(@PathVariable long id) {
		expenses.delete(id);
	}

	@Operation(summary = "운영비 승인", description = "PENDING → APPROVED. 본인 등록 건은 ADMIN 만 가능(409)")
	@PostMapping("/operating-expenses/{id}/approve")
	public ExpenseResponse approveExpense(@PathVariable long id) {
		return expenses.approve(id);
	}

	@Operation(summary = "운영비 반려", description = "PENDING → REJECTED. 본인 등록 건은 ADMIN 만 가능(409)")
	@PostMapping("/operating-expenses/{id}/reject")
	public ExpenseResponse rejectExpense(@PathVariable long id,
			@Valid @RequestBody(required = false) ExpenseRejectRequest r) {
		return expenses.reject(id, r == null ? null : r.reason());
	}

	@Operation(summary = "운영비 지급", description = "APPROVED → PAID. paymentMethod 를 비우면 등록 시 값 유지")
	@PostMapping("/operating-expenses/{id}/pay")
	public ExpenseResponse payExpense(@PathVariable long id,
			@Valid @RequestBody(required = false) ExpensePayRequest r) {
		return expenses.pay(id, r == null ? null : r.paymentMethod());
	}
}
