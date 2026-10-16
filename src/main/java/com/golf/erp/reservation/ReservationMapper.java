package com.golf.erp.reservation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReservationMapper {
	List<ReservationRow> search(@Param("keyword") String keyword, @Param("status") String status,
			@Param("offset") int offset, @Param("size") int size);

	long count(@Param("keyword") String keyword, @Param("status") String status);
}
