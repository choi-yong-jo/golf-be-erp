package com.golf.erp.settlement;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.settlement.SettlementDtos.SettlementResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 예약 결제 일별 정산.
 * <ul>
 * <li>gross: 그날 completed_at 인 PAYMENT 행 합계(PAID·SUCCEEDED, 이후 환불되어 REFUNDED 가 된 건 포함)</li>
 * <li>refund: 그날 환불된 금액 = REFUNDED 로 바뀐 결제(refunded_at 기준) + 별도 REFUND 행(completed_at 기준)</li>
 * <li>지난 날짜만 마감할 수 있고, 마감된 날짜는 해제 전까지 재계산하지 않는다.</li>
 * </ul>
 */
@Service
@Transactional
public class SettlementService {
	private static final long MAX_RANGE_DAYS = 366;

	private final SettlementRepository repo;
	private final JdbcTemplate jdbc;
	private final AuditLogger audit;
	private final Clock clock;

	public SettlementService(SettlementRepository repo, JdbcTemplate jdbc, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/** month(yyyy-MM) 또는 from~to(양 끝 포함, 최대 1년). 아무것도 없으면 이번 달. 날짜 최근 순 */
	@Transactional(readOnly = true)
	public List<SettlementResponse> list(YearMonth month, LocalDate from, LocalDate to) {
		if (month != null && (from != null || to != null))
			throw new IllegalArgumentException("month 와 from/to 는 함께 쓸 수 없습니다.");
		if (from == null && to == null) {
			YearMonth m = month == null ? YearMonth.now(clock) : month;
			from = m.atDay(1);
			to = m.atEndOfMonth();
		} else if (from == null || to == null) {
			throw new IllegalArgumentException("from 과 to 를 함께 지정하세요.");
		}
		if (to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS)
			throw new IllegalArgumentException("조회 기간은 최대 1년입니다.");
		return repo.findBySettlementDateBetweenOrderBySettlementDateDesc(from, to).stream().map(SettlementResponse::of)
				.toList();
	}

	@Transactional(readOnly = true)
	public SettlementResponse get(LocalDate date) {
		return SettlementResponse.of(find(date));
	}

	/** 해당 일자 정산을 만들거나 다시 집계한다. 미래 날짜·마감된 날짜는 불가 */
	public SettlementResponse calculate(LocalDate date) {
		Settlement s = recalculate(date);
		audit.log("CALCULATE", "settlement", s.getId(), Map.of("date", date.toString(), "net", s.getNetAmount()));
		return SettlementResponse.of(s);
	}

	/** 재계산 후 마감. 당일·미래는 결제가 더 생길 수 있어 마감할 수 없다. */
	public SettlementResponse close(LocalDate date) {
		if (!date.isBefore(LocalDate.now(clock)))
			throw new IllegalArgumentException("지난 날짜만 마감할 수 있습니다.");
		if (repo.findBySettlementDate(date).map(Settlement::isClosed).orElse(false))
			throw new ConflictException("이미 마감된 정산입니다: " + date);
		Settlement s = recalculate(date);
		s.close();
		audit.log("CLOSE", "settlement", s.getId(), Map.of("date", date.toString(), "gross", s.getGrossAmount(),
				"refund", s.getRefundAmount(), "net", s.getNetAmount()));
		return SettlementResponse.of(s);
	}

	/** 마감 해제(이후 재계산 가능) */
	public SettlementResponse reopen(LocalDate date) {
		Settlement s = find(date);
		s.reopen();
		audit.log("REOPEN", "settlement", s.getId(), Map.of("date", date.toString()));
		return SettlementResponse.of(s);
	}

	private Settlement recalculate(LocalDate date) {
		if (date.isAfter(LocalDate.now(clock)))
			throw new IllegalArgumentException("미래 날짜는 정산할 수 없습니다.");
		Settlement s = repo.findBySettlementDate(date).orElseGet(() -> new Settlement(date, LocalDateTime.now(clock)));
		LocalDateTime from = date.atStartOfDay();
		LocalDateTime to = date.plusDays(1).atStartOfDay();
		s.apply(sumGross(from, to), sumRefund(from, to));
		return repo.save(s);
	}

	private Settlement find(LocalDate date) {
		return repo.findBySettlementDate(date).orElseThrow(() -> NotFoundException.of("정산 내역", date));
	}

	/** [from, to) 에 결제 완료된 금액(이후 환불된 건 포함). 예약 앱은 PAID, 다른 시스템은 SUCCEEDED 를 쓴다. */
	private BigDecimal sumGross(LocalDateTime from, LocalDateTime to) {
		return sum("""
				SELECT COALESCE(SUM(amount), 0) FROM golf.payment
				 WHERE payment_type = 'PAYMENT' AND payment_status IN ('PAID', 'SUCCEEDED', 'REFUNDED')
				   AND completed_at >= ? AND completed_at < ?""", from, to);
	}

	/** [from, to) 에 환불된 금액: 같은 행을 REFUNDED 로 바꾸는 방식(refunded_at) + 별도 REFUND 행 방식(completed_at) */
	private BigDecimal sumRefund(LocalDateTime from, LocalDateTime to) {
		return sum("""
				SELECT COALESCE(SUM(amount), 0) FROM golf.payment
				 WHERE (payment_type = 'PAYMENT' AND payment_status = 'REFUNDED'
				        AND refunded_at >= ? AND refunded_at < ?)
				    OR (payment_type = 'REFUND' AND payment_status IN ('PAID', 'SUCCEEDED', 'REFUNDED')
				        AND completed_at >= ? AND completed_at < ?)""", from, to, from, to);
	}

	private BigDecimal sum(String sql, Object... args) {
		BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
		return v == null ? BigDecimal.ZERO : v;
	}
}
