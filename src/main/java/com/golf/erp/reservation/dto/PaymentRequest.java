package com.golf.erp.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 현장 결제. 금액은 예약 총액(totalAmount)으로 고정된다. */
public record PaymentRequest(@NotBlank @Pattern(regexp = "CARD|CASH|BANK_TRANSFER|KAKAO_PAY|NAVER_PAY") String paymentMethod) {
}
