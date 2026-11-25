package com.golf.erp.teetime.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 티타임 1건 등록. greenFee(1인) 생략 시 요금 정책으로 채우고, 정책도 없으면 비워 둔다. */
public record TeeTimeRequest(@NotNull Long golfCourseId, @Size(max = 20) String course, @NotNull LocalDate playDate,
		@NotNull LocalTime startTime, @Pattern(regexp = "9H|18H|27H|36H") String holeType,
		@Min(1) @Max(8) Short capacity, @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal greenFee) {
}
