package com.golf.erp.payment;

import com.golf.erp.payment.dto.PaymentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentMapper {
	/** requestedFrom 포함 ~ requestedTo 미포함 */
	List<PaymentRow> search(@Param("status") String status, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to, @Param("offset") int offset, @Param("size") int size);

	long count(@Param("status") String status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
