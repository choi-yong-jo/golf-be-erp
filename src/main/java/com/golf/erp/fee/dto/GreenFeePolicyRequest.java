package com.golf.erp.fee.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * golfCourseId 없으면 전체 코스 공통. validFrom/validTo 없으면 오늘~무기한(9999-12-31).
 * dayType: WEEKDAY/WEEKEND/HOLIDAY, dayOfWeek: 1(월)~7(일). timeFrom 포함, timeTo 미포함. 금액은 1인 기준.
 */
public record GreenFeePolicyRequest(Long golfCourseId, @NotBlank @Size(max = 150) String policyName,
		LocalDate validFrom, LocalDate validTo, @Pattern(regexp = "WEEKDAY|WEEKEND|HOLIDAY") String dayType,
		@Min(1) @Max(7) Short dayOfWeek, LocalTime timeFrom, LocalTime timeTo,
		@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseFee,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount, Boolean active) {
}
