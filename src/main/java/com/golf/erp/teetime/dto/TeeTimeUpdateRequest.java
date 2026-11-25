package com.golf.erp.teetime.dto;

import com.golf.erp.teetime.TeeTimeStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** 바꿀 항목만 보낸다. status 는 OPEN/BLOCKED/CLOSED 만 가능(BOOKED 는 예약으로만 바뀜). */
public record TeeTimeUpdateRequest(TeeTimeStatus status, @Min(1) @Max(8) Short capacity,
		@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal greenFee,
		@Pattern(regexp = "9H|18H|27H|36H") String holeType) {
}
