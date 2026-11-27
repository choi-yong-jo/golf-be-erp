package com.golf.erp.fee;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.common.port.GreenFeeQuoter;
import com.golf.erp.fee.FeeDtos.FeeQuote;
import com.golf.erp.fee.FeeDtos.GreenFeePolicyRequest;
import com.golf.erp.fee.FeeDtos.GreenFeePolicyResponse;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 그린피 정책 관리와 요금 계산. 다른 모듈에는 {@link GreenFeeQuoter} 로 요금을 제공한다. */
@Service
@Transactional
public class GreenFeePolicyService implements GreenFeeQuoter {
	private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

	/** 목록 정렬: 공통 정책 먼저, 골프장 순, 최근 시작일·최근 등록 순 */
	private static final Comparator<GreenFeePolicy> LIST_ORDER = Comparator
			.comparing(GreenFeePolicy::getGolfCourseId, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(GreenFeePolicy::getValidFrom, Comparator.reverseOrder())
			.thenComparing(GreenFeePolicy::getId, Comparator.reverseOrder());

	private final GreenFeePolicyRepository repo;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public GreenFeePolicyService(GreenFeePolicyRepository repo, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * @param golfCourseId 지정 시 해당 골프장 정책 + 공통 정책
	 * @param activeOnly   true 면 활성 정책만
	 * @param onDate       지정 시 그 날짜가 유효기간에 포함되는 정책만
	 */
	@Transactional(readOnly = true)
	public List<GreenFeePolicyResponse> list(Long golfCourseId, boolean activeOnly, LocalDate onDate) {
		Specification<GreenFeePolicy> course = golfCourseId == null ? null
				: (root, q, cb) -> cb.or(cb.equal(root.get("golfCourseId"), golfCourseId),
						cb.isNull(root.get("golfCourseId")));
		Specification<GreenFeePolicy> spec = Specification.allOf(course,
				activeOnly ? Specs.eq("active", true) : null, Specs.lte("validFrom", onDate),
				Specs.gte("validTo", onDate));
		return repo.findAll(spec).stream().sorted(LIST_ORDER).map(GreenFeePolicyResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public GreenFeePolicyResponse get(long id) {
		return GreenFeePolicyResponse.of(find(id));
	}

	public GreenFeePolicyResponse create(GreenFeePolicyRequest r) {
		GreenFeePolicy p = new GreenFeePolicy(LocalDateTime.now(clock));
		apply(p, r);
		return GreenFeePolicyResponse.of(repo.save(p));
	}

	public GreenFeePolicyResponse update(long id, GreenFeePolicyRequest r) {
		GreenFeePolicy p = find(id);
		apply(p, r);
		audit.log("UPDATE", "green_fee_policy", id, Map.of("policyName", p.getPolicyName()));
		return GreenFeePolicyResponse.of(p);
	}

	/** 비활성 처리. 과거 예약·티타임 요금의 근거이므로 행은 남긴다. */
	public void deactivate(long id) {
		GreenFeePolicy p = find(id);
		if (!p.isActive())
			return;
		p.deactivate();
		audit.log("DEACTIVATE", "green_fee_policy", id, Map.of("policyName", p.getPolicyName()));
	}

	/** 골프장·날짜·시각에 적용될 정책 */
	@Transactional(readOnly = true)
	public Optional<GreenFeePolicy> resolve(long golfCourseId, LocalDate date, LocalTime time) {
		return table(golfCourseId, date, date).policyAt(date, time);
	}

	/** [from, to] 기간의 후보 정책을 한 번에 읽어 둔 요금표. 슬롯이 많은 일괄 처리용 */
	@Transactional(readOnly = true)
	public GreenFeeTable table(long golfCourseId, LocalDate from, LocalDate to) {
		return new GreenFeeTable(repo.findCandidates(golfCourseId, from, to));
	}

	@Transactional(readOnly = true)
	public FeeQuote quote(long golfCourseId, LocalDate date, LocalTime time) {
		refs.require(Ref.GOLF_COURSE, golfCourseId);
		return resolve(golfCourseId, date, time)
				.map(p -> new FeeQuote(golfCourseId, date, time, p.getId(), p.getPolicyName(), p.getBaseFee(),
						p.getDiscountAmount(), p.netFee()))
				.orElse(new FeeQuote(golfCourseId, date, time, null, null, null, null, null));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<BigDecimal> perPlayerFee(long golfCourseId, LocalDateTime playAt) {
		return resolve(golfCourseId, playAt.toLocalDate(), playAt.toLocalTime()).map(GreenFeePolicy::netFee);
	}

	private void apply(GreenFeePolicy p, GreenFeePolicyRequest r) {
		refs.requireIfPresent(Ref.GOLF_COURSE, r.golfCourseId());
		LocalDate from = r.validFrom() == null ? LocalDate.now(clock) : r.validFrom();
		LocalDate to = r.validTo() == null ? OPEN_END : r.validTo();
		if (to.isBefore(from))
			throw new IllegalArgumentException("유효기간 종료일은 시작일 이후여야 합니다.");
		if (r.timeFrom() != null && r.timeTo() != null && !r.timeTo().isAfter(r.timeFrom()))
			throw new IllegalArgumentException("종료 시각은 시작 시각 이후여야 합니다.");
		BigDecimal base = r.baseFee().setScale(2, RoundingMode.HALF_UP);
		BigDecimal discount = (r.discountAmount() == null ? BigDecimal.ZERO : r.discountAmount()).setScale(2,
				RoundingMode.HALF_UP);
		if (discount.compareTo(base) > 0)
			throw new IllegalArgumentException("할인액은 기본 요금을 넘을 수 없습니다.");
		p.update(r.golfCourseId(), r.policyName().trim(), from, to, blankToNull(r.dayType()), r.dayOfWeek(),
				r.timeFrom(), r.timeTo(), base, discount, r.active() == null || r.active());
	}

	private GreenFeePolicy find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("요금 정책", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
