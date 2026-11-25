package com.golf.erp.fee;

import com.golf.erp.common.NotFoundException;
import com.golf.erp.course.GolfCourseService;
import com.golf.erp.fee.dto.FeeQuote;
import com.golf.erp.fee.dto.GreenFeePolicyRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class GreenFeePolicyService {
	private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

	/**
	 * 여러 정책이 맞을 때 우선순위: 특정 코스 > 공통, 요일 지정 > 미지정, 평일/주말 지정 > 미지정, 시간대 지정 > 미지정,
	 * 최근 시작 정책 > 이전 정책, 최근 등록 > 이전 등록.
	 */
	private static final Comparator<GreenFeePolicy> PRIORITY = Comparator
			.comparing((GreenFeePolicy p) -> p.getGolfCourseId() != null)
			.thenComparing(p -> p.getDayOfWeek() != null)
			.thenComparing(p -> p.getDayType() != null)
			.thenComparing(p -> p.getTimeFrom() != null || p.getTimeTo() != null)
			.thenComparing(GreenFeePolicy::getValidFrom)
			.thenComparing(GreenFeePolicy::getGreenFeePolicyId).reversed();

	private final GreenFeePolicyRepository repo;
	private final GolfCourseService courseService;
	private final Clock clock;

	public GreenFeePolicyService(GreenFeePolicyRepository repo, GolfCourseService courseService, Clock clock) {
		this.repo = repo;
		this.courseService = courseService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<GreenFeePolicy> search(Long courseId, boolean activeOnly) {
		return repo.search(courseId, activeOnly);
	}

	@Transactional(readOnly = true)
	public GreenFeePolicy get(long id) {
		return repo.findById(id).orElseThrow(() -> new NotFoundException("요금 정책을 찾을 수 없습니다: " + id));
	}

	public GreenFeePolicy create(GreenFeePolicyRequest r) {
		GreenFeePolicy p = new GreenFeePolicy(LocalDateTime.now(clock));
		apply(p, r);
		return repo.save(p);
	}

	public GreenFeePolicy update(long id, GreenFeePolicyRequest r) {
		GreenFeePolicy p = get(id);
		apply(p, r);
		return p;
	}

	/** 비활성 처리. 과거 예약 금액의 근거이므로 행은 남긴다. */
	public GreenFeePolicy deactivate(long id) {
		GreenFeePolicy p = get(id);
		p.deactivate();
		return p;
	}

	/** 코스·날짜·시각에 적용될 정책 */
	@Transactional(readOnly = true)
	public Optional<GreenFeePolicy> resolve(long courseId, LocalDate date, LocalTime time) {
		return repo.findCandidates(courseId, date).stream().filter(p -> p.matches(date, time)).min(PRIORITY);
	}

	@Transactional(readOnly = true)
	public FeeQuote quote(long courseId, LocalDate date, LocalTime time) {
		courseService.get(courseId);
		return resolve(courseId, date, time)
				.map(p -> new FeeQuote(courseId, date, time, p.getGreenFeePolicyId(), p.getPolicyName(),
						p.getBaseFee(), p.getDiscountAmount(), p.netFee()))
				.orElse(new FeeQuote(courseId, date, time, null, null, null, null, null));
	}

	private void apply(GreenFeePolicy p, GreenFeePolicyRequest r) {
		if (r.golfCourseId() != null)
			courseService.get(r.golfCourseId());
		LocalDate from = r.validFrom() == null ? LocalDate.now(clock) : r.validFrom();
		LocalDate to = r.validTo() == null ? OPEN_END : r.validTo();
		if (to.isBefore(from))
			throw new IllegalArgumentException("유효기간 종료일은 시작일 이후여야 합니다.");
		if (r.timeFrom() != null && r.timeTo() != null && !r.timeTo().isAfter(r.timeFrom()))
			throw new IllegalArgumentException("종료 시각은 시작 시각 이후여야 합니다.");
		BigDecimal discount = r.discountAmount() == null ? BigDecimal.ZERO : r.discountAmount();
		if (discount.compareTo(r.baseFee()) > 0)
			throw new IllegalArgumentException("할인액은 기본 요금을 넘을 수 없습니다.");
		p.apply(r.golfCourseId(), r.policyName().trim(), from, to, r.dayType(), r.dayOfWeek(), r.timeFrom(),
				r.timeTo(), r.baseFee(), discount, r.active() == null || r.active());
	}
}
