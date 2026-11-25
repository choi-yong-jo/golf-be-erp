package com.golf.erp.teetime.dto;

/**
 * requested: 계산된 슬롯 수, created: 새로 만든 수, skippedExisting: 이미 있던 슬롯, skippedClosure: 휴장 구간,
 * withoutGreenFee: 요금을 정하지 못해 그린피 없이 만든 수(예약 앱에는 노출되지 않음)
 */
public record TeeTimeGenerateResult(int requested, int created, int skippedExisting, int skippedClosure,
		int withoutGreenFee) {
}
