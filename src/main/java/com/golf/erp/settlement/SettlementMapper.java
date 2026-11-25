package com.golf.erp.settlement;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface SettlementMapper {
	/** [from, to) 에 결제 완료된 금액 (이후 환불된 건 포함) */
	BigDecimal sumGross(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	/** [from, to) 에 환불된 금액 (REFUNDED 로 바뀐 결제 + 별도 REFUND 행) */
	BigDecimal sumRefund(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
