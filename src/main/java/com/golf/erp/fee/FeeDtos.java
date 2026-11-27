package com.golf.erp.fee;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 그린피 정책 API 요청/응답 */
public final class FeeDtos {
	private FeeDtos() {
	}

	/**
	 * golfCourseId 없으면 전체 골프장 공통. validFrom/validTo 없으면 오늘~무기한(9999-12-31).
	 * dayType: WEEKDAY/WEEKEND/HOLIDAY(HOLIDAY 는 자동 적용되지 않음), dayOfWeek: 1(월)~7(일).
	 * timeFrom 포함, timeTo 미포함. 금액은 1인 기준. active 생략 시 true.
	 */
	public record GreenFeePolicyRequest(Long golfCourseId, @NotBlank @Size(max = 150) String policyName,
			LocalDate validFrom, LocalDate validTo,
			@Pattern(regexp = "WEEKDAY|WEEKEND|HOLIDAY", message = "WEEKDAY, WEEKEND, HOLIDAY 중 하나여야 합니다.") String dayType,
			@Min(1) @Max(7) Short dayOfWeek, LocalTime timeFrom, LocalTime timeTo,
			@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseFee,
			@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount, Boolean active) {
	}

	/** netFee: 1인 실결제 요금(기본 요금 - 할인) */
	public record GreenFeePolicyResponse(Long id, Long golfCourseId, String policyName, LocalDate validFrom,
			LocalDate validTo, String dayType, Short dayOfWeek, LocalTime timeFrom, LocalTime timeTo,
			BigDecimal baseFee, BigDecimal discountAmount, BigDecimal netFee, boolean active,
			LocalDateTime createdAt) {

		static GreenFeePolicyResponse of(GreenFeePolicy p) {
			return new GreenFeePolicyResponse(p.getId(), p.getGolfCourseId(), p.getPolicyName(), p.getValidFrom(),
					p.getValidTo(), p.getDayType(), p.getDayOfWeek(), p.getTimeFrom(), p.getTimeTo(), p.getBaseFee(),
					p.getDiscountAmount(), p.netFee(), p.isActive(), p.getCreatedAt());
		}
	}

	/** 요금 조회 결과. policyId 가 null 이면 적용 가능한 정책이 없다(금액도 null). 금액은 1인 기준. */
	public record FeeQuote(long golfCourseId, LocalDate date, LocalTime time, Long policyId, String policyName,
			BigDecimal baseFee, BigDecimal discountAmount, BigDecimal netFee) {
	}
}
