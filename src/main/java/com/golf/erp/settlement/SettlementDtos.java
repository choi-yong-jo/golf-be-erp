package com.golf.erp.settlement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 일별 정산 API 응답 */
public final class SettlementDtos {
	private SettlementDtos() {
	}

	public record SettlementResponse(Long id, LocalDate settlementDate, BigDecimal grossAmount,
			BigDecimal refundAmount, BigDecimal netAmount, SettlementStatus status, LocalDateTime createdAt) {

		static SettlementResponse of(Settlement s) {
			return new SettlementResponse(s.getId(), s.getSettlementDate(), s.getGrossAmount(), s.getRefundAmount(),
					s.getNetAmount(), s.getStatus(), s.getCreatedAt());
		}
	}
}
