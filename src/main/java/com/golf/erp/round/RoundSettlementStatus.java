package com.golf.erp.round;

import java.math.BigDecimal;

/** 라운드 수납 상태(round.settlement_status). 요금·수납 합계로 계산해 저장한다. */
public enum RoundSettlementStatus {
	UNPAID, PARTIAL, PAID;

	/**
	 * @param chargeTotal 취소되지 않은 요금 합계
	 * @param paidTotal   취소되지 않은 수납 합계
	 */
	public static RoundSettlementStatus of(BigDecimal chargeTotal, BigDecimal paidTotal) {
		if (chargeTotal.signum() > 0 && paidTotal.compareTo(chargeTotal) >= 0)
			return PAID;
		return paidTotal.signum() > 0 ? PARTIAL : UNPAID;
	}
}
