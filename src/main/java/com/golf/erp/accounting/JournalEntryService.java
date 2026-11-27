package com.golf.erp.accounting;

import com.golf.erp.accounting.AccountingDtos.JournalEntryRequest;
import com.golf.erp.accounting.AccountingDtos.JournalEntryResponse;
import com.golf.erp.accounting.AccountingDtos.JournalEntrySummary;
import com.golf.erp.accounting.AccountingDtos.JournalLineRequest;
import com.golf.erp.accounting.AccountingDtos.TrialBalanceResponse;
import com.golf.erp.accounting.AccountingDtos.TrialBalanceRow;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 회계 전표. DRAFT 로 만들고 줄을 자유롭게 고친 뒤 전기(POSTED)한다. 전기된 전표는 고칠 수 없고 무효(VOID) 처리만 된다.
 * 시산표는 POSTED 전표만 집계한다.
 */
@Service
@Transactional
public class JournalEntryService {
	private final JournalEntryRepository repo;
	private final JournalLineRepository lineRepo;
	private final AccountRepository accounts;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final JdbcTemplate jdbc;
	private final Clock clock;

	public JournalEntryService(JournalEntryRepository repo, JournalLineRepository lineRepo, AccountRepository accounts,
			RefChecker refs, AuditLogger audit, JdbcTemplate jdbc, Clock clock) {
		this.repo = repo;
		this.lineRepo = lineRepo;
		this.accounts = accounts;
		this.refs = refs;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** from/to: 회계일(business_date) 범위(포함). keyword: 전표 번호·적요 */
	@Transactional(readOnly = true)
	public PageResult<JournalEntrySummary> list(Long golfCourseId, LocalDate from, LocalDate to, JournalStatus status,
			String sourceType, Long sourceId, String keyword, int page, int size) {
		requireRange(from, to);
		Specification<JournalEntry> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.gte("businessDate", from), Specs.lte("businessDate", to), Specs.eq("status", status),
				Specs.eq("sourceType", sourceType == null ? null : sourceType.trim()), Specs.eq("sourceId", sourceId),
				Specs.like(keyword, "entryNo", "description"));
		Page<JournalEntry> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("businessDate"), Sort.Order.desc("id"))));
		Map<Long, BigDecimal[]> totals = new HashMap<>();
		List<Long> ids = result.getContent().stream().map(JournalEntry::getId).toList();
		if (!ids.isEmpty())
			for (Object[] row : lineRepo.sumByEntryIds(ids))
				totals.put((Long) row[0], new BigDecimal[] { (BigDecimal) row[1], (BigDecimal) row[2] });
		return PageResult.of(result, e -> {
			BigDecimal[] t = totals.getOrDefault(e.getId(), new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			return JournalEntrySummary.of(e, t[0], t[1]);
		});
	}

	@Transactional(readOnly = true)
	public JournalEntryResponse get(long id) {
		return toResponse(find(id));
	}

	public JournalEntryResponse create(JournalEntryRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		List<JournalLine> lines = toLines(r.lines());
		LocalDateTime now = LocalDateTime.now(clock);
		JournalEntry e = new JournalEntry(DocumentNo.temporary(), CurrentUser.idOrNull(), now);
		applyHeader(e, r);
		e.replaceLines(lines);
		repo.saveAndFlush(e);
		e.assignNo(DocumentNo.of("JE", e.getBusinessDate(), e.getId()));
		return toResponse(e);
	}

	/** DRAFT 만. 머리 정보를 바꾸고 줄은 요청 내용으로 모두 교체한다. 전표 번호는 바뀌지 않는다. */
	public JournalEntryResponse update(long id, JournalEntryRequest r) {
		JournalEntry e = lock(id);
		e.requireDraft("수정");
		if (!r.golfCourseId().equals(e.getGolfCourseId()))
			refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		List<JournalLine> lines = toLines(r.lines());
		applyHeader(e, r);
		e.replaceLines(lines);
		return toResponse(e);
	}

	/** 두 줄 이상, 차변 합계 = 대변 합계 > 0, 모든 계정이 활성이어야 전기된다. */
	public JournalEntryResponse post(long id) {
		JournalEntry e = lock(id);
		e.requireDraft("전기");
		Map<Long, Account> used = accountMap(e);
		for (JournalLine l : e.getLines()) {
			Account a = used.get(l.getAccountId());
			if (a == null)
				throw NotFoundException.of("계정과목", l.getAccountId());
			if (!a.isActive())
				throw new ConflictException("비활성 계정이 포함되어 있습니다: " + a.getCode() + " " + a.getName());
		}
		e.post(LocalDateTime.now(clock));
		audit.log("POST", "journal_entry", id, Map.of("entryNo", e.getEntryNo(), "amount", e.totalDebit()));
		return JournalEntryResponse.of(e, used);
	}

	public JournalEntryResponse voidEntry(long id, String reason) {
		JournalEntry e = lock(id);
		e.voidEntry();
		Map<String, Object> details = new HashMap<>();
		details.put("entryNo", e.getEntryNo());
		details.put("amount", e.totalDebit());
		if (reason != null && !reason.isBlank())
			details.put("reason", reason.trim());
		audit.log("VOID", "journal_entry", id, details);
		return toResponse(e);
	}

	/** DRAFT 만 삭제(줄도 함께). 전기된 전표는 무효 처리한다. */
	public void delete(long id) {
		JournalEntry e = lock(id);
		e.requireDraft("삭제");
		String entryNo = e.getEntryNo();
		repo.delete(e);
		audit.log("DELETE", "journal_entry", id, Map.of("entryNo", entryNo));
	}

	/**
	 * 시산표. POSTED 전표만 계정별로 차변·대변 합계를 낸다. golfCourseId·from·to 는 모두 선택(비우면 전체).
	 */
	@Transactional(readOnly = true)
	public TrialBalanceResponse trialBalance(Long golfCourseId, LocalDate from, LocalDate to) {
		requireRange(from, to);
		StringBuilder sql = new StringBuilder(
				"SELECT a.account_id, a.account_code, a.account_name, a.account_type,"
						+ " COALESCE(SUM(l.debit_amount), 0) AS debit, COALESCE(SUM(l.credit_amount), 0) AS credit"
						+ " FROM golf.journal_line l"
						+ " JOIN golf.journal_entry e ON e.journal_entry_id = l.journal_entry_id"
						+ " JOIN golf.account a ON a.account_id = l.account_id"
						+ " WHERE e.entry_status = 'POSTED'");
		List<Object> args = new ArrayList<>();
		if (golfCourseId != null) {
			sql.append(" AND e.golf_course_id = ?");
			args.add(golfCourseId);
		}
		if (from != null) {
			sql.append(" AND e.business_date >= ?");
			args.add(Date.valueOf(from));
		}
		if (to != null) {
			sql.append(" AND e.business_date <= ?");
			args.add(Date.valueOf(to));
		}
		sql.append(" GROUP BY a.account_id, a.account_code, a.account_name, a.account_type ORDER BY a.account_code");
		List<TrialBalanceRow> rows = jdbc.query(sql.toString(), (rs, i) -> {
			AccountType type = AccountType.valueOf(rs.getString("account_type"));
			BigDecimal d = rs.getBigDecimal("debit");
			BigDecimal c = rs.getBigDecimal("credit");
			return new TrialBalanceRow(rs.getLong("account_id"), rs.getString("account_code"),
					rs.getString("account_name"), type, d, c, type.isDebitNormal() ? d.subtract(c) : c.subtract(d));
		}, args.toArray());
		BigDecimal debit = rows.stream().map(TrialBalanceRow::debitTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal credit = rows.stream().map(TrialBalanceRow::creditTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new TrialBalanceResponse(golfCourseId, from, to, rows, debit, credit, debit.compareTo(credit) == 0);
	}

	private void applyHeader(JournalEntry e, JournalEntryRequest r) {
		e.update(r.golfCourseId(), r.businessDate(), blankToNull(r.description()), blankToNull(r.sourceType()),
				r.sourceId());
	}

	/** 줄 요청 → 엔티티. 계정은 존재하고 활성이어야 한다. */
	private List<JournalLine> toLines(List<JournalLineRequest> requests) {
		Set<Long> ids = requests.stream().map(JournalLineRequest::accountId).collect(Collectors.toSet());
		Map<Long, Account> found = accounts.findAllById(ids).stream()
				.collect(Collectors.toMap(Account::getId, Function.identity()));
		List<JournalLine> lines = new ArrayList<>();
		for (JournalLineRequest l : requests) {
			Account a = found.get(l.accountId());
			if (a == null)
				throw NotFoundException.of("계정과목", l.accountId());
			if (!a.isActive())
				throw new ConflictException("비활성 계정은 전표에 쓸 수 없습니다: " + a.getCode() + " " + a.getName());
			lines.add(new JournalLine(a.getId(), l.debitAmount(), l.creditAmount(), blankToNull(l.memo())));
		}
		return lines;
	}

	private Map<Long, Account> accountMap(JournalEntry e) {
		Set<Long> ids = e.getLines().stream().map(JournalLine::getAccountId).collect(Collectors.toSet());
		return accounts.findAllById(ids).stream().collect(Collectors.toMap(Account::getId, Function.identity()));
	}

	private JournalEntryResponse toResponse(JournalEntry e) {
		return JournalEntryResponse.of(e, accountMap(e));
	}

	private static void requireRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
	}

	private JournalEntry lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("전표", id));
	}

	private JournalEntry find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("전표", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
