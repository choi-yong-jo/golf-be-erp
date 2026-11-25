package com.golf.erp.teetime.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 기간 일괄 생성. fromDate~toDate(최대 62일) 각 날짜에 firstTime~lastTime 을 intervalMinutes 간격으로 만든다.
 * daysOfWeek(1=월~7=일)를 주면 해당 요일만. greenFee 생략 시 슬롯마다 요금 정책으로 채운다.
 */
public record TeeTimeGenerateRequest(@NotNull Long golfCourseId, @Size(max = 20) String course,
		@NotNull LocalDate fromDate, @NotNull LocalDate toDate, @NotNull LocalTime firstTime,
		@NotNull LocalTime lastTime, @NotNull @Min(5) @Max(120) Integer intervalMinutes,
		Set<@Min(1) @Max(7) Integer> daysOfWeek, @Pattern(regexp = "9H|18H|27H|36H") String holeType,
		@Min(1) @Max(8) Short capacity, @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal greenFee) {
}
