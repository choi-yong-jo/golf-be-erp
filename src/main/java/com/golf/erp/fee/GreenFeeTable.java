package com.golf.erp.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 한 골프장의 후보 정책을 메모리에 들고 날짜·시각별 적용 정책을 고른다.
 * 티타임 일괄 생성처럼 슬롯이 많을 때 슬롯마다 DB 를 조회하지 않으려고 쓴다.
 */
public final class GreenFeeTable {
	/**
	 * 여러 정책이 맞을 때 우선순위: 특정 골프장 > 공통, 요일 지정 > 미지정, 평일/주말 지정 > 미지정, 시간대 지정 > 미지정,
	 * 최근 시작 정책 > 이전 정책, 최근 등록 > 이전 등록.
	 */
	static final Comparator<GreenFeePolicy> PRIORITY = Comparator
			.comparing((GreenFeePolicy p) -> p.getGolfCourseId() != null)
			.thenComparing(p -> p.getDayOfWeek() != null)
			.thenComparing(p -> p.getDayType() != null)
			.thenComparing(p -> p.getTimeFrom() != null || p.getTimeTo() != null)
			.thenComparing(GreenFeePolicy::getValidFrom)
			.thenComparing(GreenFeePolicy::getId).reversed();

	private final List<GreenFeePolicy> candidates;

	GreenFeeTable(List<GreenFeePolicy> candidates) {
		this.candidates = candidates;
	}

	/** 해당 날짜·시각에 적용되는 정책 */
	public Optional<GreenFeePolicy> policyAt(LocalDate date, LocalTime time) {
		return candidates.stream()
				.filter(p -> !date.isBefore(p.getValidFrom()) && !date.isAfter(p.getValidTo()))
				.filter(p -> p.matches(date, time)).min(PRIORITY);
	}

	/** 해당 날짜·시각의 1인 실결제 그린피. 맞는 정책이 없으면 empty */
	public Optional<BigDecimal> netFeeAt(LocalDate date, LocalTime time) {
		return policyAt(date, time).map(GreenFeePolicy::netFee);
	}
}
