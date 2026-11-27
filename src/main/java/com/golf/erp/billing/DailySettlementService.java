package com.golf.erp.billing;

import com.golf.erp.billing.BillingDtos.SettlementCalculateRequest;
import com.golf.erp.billing.BillingDtos.SettlementItemResponse;
import com.golf.erp.billing.BillingDtos.SettlementResponse;
import com.golf.erp.billing.BillingDtos.SettlementSummary;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.round.GolfRoundRepository;
import com.golf.erp.round.RoundStatus;
import com.golf.erp.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 일 마감(골프장·영업일 단위). 영업일 = 라운드의 play_date, 취소(CANCELLED) 라운드는 집계에서 뺀다.
 * <ul>
 * <li>계산(calculate): OPEN 일 때만. 없으면 만들고, 있으면 다시 계산해 금액·항목을 바꾼다.</li>
 * <li>마감(close): 체크인·플레이 중인 라운드가 없어야 한다. 마감 직전에 다시 계산한 값이 스냅샷으로 남는다.</li>
 * <li>마감 해제(reopen): OPEN 으로 돌리고 다시 계산한다.</li>
 * </ul>
 * 계산·마감은 정산 행 → 그날 라운드 행 순서로 잠근다. 요금·수납 변경은 라운드 행을 잠근 뒤 마감 여부를 보므로
 * 마감 계산과 요금 변경이 엇갈려 스냅샷이 어긋나는 일이 없다.
 */
@Service
@Transactional
public class DailySettlementService {
	private static final String ROUND_FILTER = " FROM golf.round r WHERE r.golf_course_id = :g AND r.play_date = :d"
			+ " AND r.round_status <> 'CANCELLED'";

	private final DailySettlementRepository repo;
	private final DailySettlementItemRepository items;
	private final GolfRoundRepository rounds;
	private final NamedParameterJdbcTemplate jdbc;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public DailySettlementService(DailySettlementRepository repo, DailySettlementItemRepository items,
			GolfRoundRepository rounds, NamedParameterJdbcTemplate jdbc, RefChecker refs, AuditLogger audit,
			Clock clock) {
		this.repo = repo;
		this.items = items;
		this.rounds = rounds;
		this.jdbc = jdbc;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** 집계 결과(저장 전) */
	record Totals(int roundCount, int playerCount, BigDecimal chargeTotal, BigDecimal discountTotal,
			BigDecimal paymentTotal, List<DailySettlementItem> items) {
	}

	@Transactional(readOnly = true)
	public PageResult<SettlementSummary> list(Long golfCourseId, LocalDate from, LocalDate to,
			DailySettlementStatus status, int page, int size) {
		Specification<DailySettlement> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("status", status), Specs.gte("businessDate", from), Specs.lte("businessDate", to));
		return PageResult.of(repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Direction.DESC, "businessDate").and(Sort.by("golfCourseId")))),
				SettlementSummary::of);
	}

	@Transactional(readOnly = true)
	public SettlementResponse get(long id) {
		DailySettlement s = find(id);
		return toResponse(s, items.findByDailySettlementIdOrderByItemTypeAscItemCodeAsc(id));
	}

	/** 저장하지 않고 지금 기준으로 집계한 값(마감 전 확인용). 저장된 정산이 있으면 상태·메모를 함께 보여준다. */
	@Transactional(readOnly = true)
	public SettlementResponse preview(long golfCourseId, LocalDate businessDate) {
		refs.require(Ref.GOLF_COURSE, golfCourseId);
		Totals t = compute(golfCourseId, businessDate);
		DailySettlement saved = repo.findByGolfCourseIdAndBusinessDate(golfCourseId, businessDate).orElse(null);
		return new SettlementResponse(saved == null ? null : saved.getId(), golfCourseId, businessDate,
				saved == null ? DailySettlementStatus.OPEN : saved.getStatus(), t.roundCount(), t.playerCount(),
				t.chargeTotal(), t.discountTotal(), t.paymentTotal(), t.chargeTotal().subtract(t.paymentTotal()),
				saved == null ? null : saved.getClosedAt(), saved == null ? null : saved.getClosedBy(),
				saved == null ? null : saved.getMemo(), activeRounds(golfCourseId, businessDate),
				itemResponses(t.items(), SettlementItemType.CHARGE), itemResponses(t.items(), SettlementItemType.PAYMENT),
				saved == null ? null : saved.getCreatedAt(), saved == null ? null : saved.getUpdatedAt());
	}

	/** 계산(없으면 생성). CLOSED 면 409 */
	public SettlementResponse calculate(SettlementCalculateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		DailySettlement s = repo.findForUpdate(r.golfCourseId(), r.businessDate()).orElseGet(() -> create(r));
		s.requireOpen();
		if (r.memo() != null)
			s.changeMemo(blankToNull(r.memo()), now());
		return recalculate(s);
	}

	/** 마감: 다시 계산한 값을 스냅샷으로 남기고 CLOSED 로 바꾼다. */
	public SettlementResponse close(long id, String memo) {
		DailySettlement s = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("일 마감", id));
		s.requireOpen();
		if (s.getBusinessDate().isAfter(LocalDate.now(clock)))
			throw new ConflictException("아직 오지 않은 영업일은 마감할 수 없습니다: " + s.getBusinessDate());
		rounds.lockAllOn(s.getGolfCourseId(), s.getBusinessDate());
		int active = activeRounds(s.getGolfCourseId(), s.getBusinessDate());
		if (active > 0)
			throw new ConflictException("체크인·플레이 중인 라운드가 " + active + "팀 남아 있어 마감할 수 없습니다. 라운드를 종료하거나 취소하세요.");
		LocalDateTime now = now();
		if (memo != null && !memo.isBlank())
			s.changeMemo(memo.trim(), now);
		SettlementResponse before = recalculate(s);
		s.close(CurrentUser.idOrNull(), now);
		audit.log("CLOSE", "daily_settlement", id,
				Map.of("golfCourseId", s.getGolfCourseId(), "businessDate", s.getBusinessDate().toString(),
						"chargeTotal", s.getChargeTotal(), "paymentTotal", s.getPaymentTotal(), "unpaidTotal",
						s.getUnpaidTotal()));
		return toResponse(s, before);
	}

	/** 마감 해제: OPEN 으로 돌리고 다시 계산한다. */
	public SettlementResponse reopen(long id, String reason) {
		DailySettlement s = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("일 마감", id));
		LocalDateTime closedAt = s.getClosedAt();
		s.reopen(now());
		audit.log("REOPEN", "daily_settlement", id, Map.of("golfCourseId", s.getGolfCourseId(), "businessDate",
				s.getBusinessDate().toString(), "closedAt", String.valueOf(closedAt), "reason", reason.trim()));
		return recalculate(s);
	}

	// ── 내부 ──

	private DailySettlement create(SettlementCalculateRequest r) {
		try {
			return repo.saveAndFlush(new DailySettlement(r.golfCourseId(), r.businessDate(), now()));
		} catch (DataIntegrityViolationException e) {
			throw new ConflictException("다른 직원이 같은 날의 정산을 만들고 있습니다. 다시 조회한 뒤 시도하세요.");
		}
	}

	/** 그날 라운드를 잠그고 다시 집계해 정산 행과 항목을 바꾼다(OPEN 일 때만). */
	private SettlementResponse recalculate(DailySettlement s) {
		rounds.lockAllOn(s.getGolfCourseId(), s.getBusinessDate());
		Totals t = compute(s.getGolfCourseId(), s.getBusinessDate());
		s.applyTotals(t.roundCount(), t.playerCount(), t.chargeTotal(), t.discountTotal(), t.paymentTotal(), now());
		items.deleteBySettlement(s.getId());
		List<DailySettlementItem> saved = new ArrayList<>();
		for (DailySettlementItem i : t.items())
			saved.add(items.save(new DailySettlementItem(s.getId(), i.getItemType(), i.getItemCode(), i.getItemCount(),
					i.getAmount())));
		repo.flush();
		return toResponse(s, saved);
	}

	private Totals compute(long golfCourseId, LocalDate date) {
		MapSqlParameterSource p = new MapSqlParameterSource("g", golfCourseId).addValue("d", date);
		Integer roundCount = jdbc.queryForObject("SELECT count(*)" + ROUND_FILTER, p, Integer.class);
		Integer playerCount = jdbc.queryForObject("SELECT count(*) FROM golf.round_player rp WHERE rp.round_id IN"
				+ " (SELECT r.round_id" + ROUND_FILTER + ")", p, Integer.class);
		List<DailySettlementItem> list = new ArrayList<>();
		BigDecimal[] charge = { zero(), zero() };
		jdbc.query("SELECT c.charge_type, count(*), coalesce(sum(c.amount), 0), coalesce(sum(c.discount_amount), 0)"
				+ " FROM golf.round_charge c WHERE c.is_void = FALSE AND c.round_id IN (SELECT r.round_id" + ROUND_FILTER
				+ ") GROUP BY c.charge_type ORDER BY c.charge_type", p, rs -> {
					list.add(new DailySettlementItem(null, SettlementItemType.CHARGE, rs.getString(1), rs.getInt(2),
							rs.getBigDecimal(3)));
					charge[0] = charge[0].add(rs.getBigDecimal(3));
					charge[1] = charge[1].add(rs.getBigDecimal(4));
				});
		BigDecimal[] paid = { zero() };
		jdbc.query("SELECT pm.payment_method, count(*), coalesce(sum(pm.amount), 0) FROM golf.round_payment pm"
				+ " WHERE pm.payment_status = 'PAID' AND pm.round_id IN (SELECT r.round_id" + ROUND_FILTER
				+ ") GROUP BY pm.payment_method ORDER BY pm.payment_method", p, rs -> {
					list.add(new DailySettlementItem(null, SettlementItemType.PAYMENT, rs.getString(1), rs.getInt(2),
							rs.getBigDecimal(3)));
					paid[0] = paid[0].add(rs.getBigDecimal(3));
				});
		return new Totals(roundCount == null ? 0 : roundCount, playerCount == null ? 0 : playerCount, charge[0],
				charge[1], paid[0], list);
	}

	private int activeRounds(long golfCourseId, LocalDate date) {
		return (int) rounds.countByGolfCourseIdAndPlayDateAndStatusIn(golfCourseId, date,
				List.of(RoundStatus.CHECKED_IN, RoundStatus.PLAYING));
	}

	private SettlementResponse toResponse(DailySettlement s, List<DailySettlementItem> list) {
		return new SettlementResponse(s.getId(), s.getGolfCourseId(), s.getBusinessDate(), s.getStatus(),
				s.getRoundCount(), s.getPlayerCount(), s.getChargeTotal(), s.getDiscountTotal(), s.getPaymentTotal(),
				s.getUnpaidTotal(), s.getClosedAt(), s.getClosedBy(), s.getMemo(),
				activeRounds(s.getGolfCourseId(), s.getBusinessDate()), itemResponses(list, SettlementItemType.CHARGE),
				itemResponses(list, SettlementItemType.PAYMENT), s.getCreatedAt(), s.getUpdatedAt());
	}

	/** 마감 직후: 항목은 방금 계산한 응답의 것을 그대로 쓰고 상태·마감 정보만 새로 채운다. */
	private SettlementResponse toResponse(DailySettlement s, SettlementResponse calculated) {
		return new SettlementResponse(s.getId(), s.getGolfCourseId(), s.getBusinessDate(), s.getStatus(),
				s.getRoundCount(), s.getPlayerCount(), s.getChargeTotal(), s.getDiscountTotal(), s.getPaymentTotal(),
				s.getUnpaidTotal(), s.getClosedAt(), s.getClosedBy(), s.getMemo(), calculated.activeRoundCount(),
				calculated.chargeItems(), calculated.paymentItems(), s.getCreatedAt(), s.getUpdatedAt());
	}

	private static List<SettlementItemResponse> itemResponses(List<DailySettlementItem> list,
			SettlementItemType type) {
		return list.stream().filter(i -> i.getItemType() == type).map(SettlementItemResponse::of).toList();
	}

	private DailySettlement find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("일 마감", id));
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(2);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
