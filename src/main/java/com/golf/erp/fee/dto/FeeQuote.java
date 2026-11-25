package com.golf.erp.fee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 요금 조회 결과. policyId 가 null 이면 적용 가능한 정책이 없다. 금액은 1인 기준. */
public record FeeQuote(long golfCourseId, LocalDate date, LocalTime time, Long policyId, String policyName,
		BigDecimal baseFee, BigDecimal discountAmount, BigDecimal netFee) {
}
