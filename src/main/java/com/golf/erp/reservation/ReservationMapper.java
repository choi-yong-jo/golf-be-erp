package com.golf.erp.reservation;

import com.golf.erp.reservation.dto.ReservationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {
	/** keyword: 예약번호, 회원 이름, 전화번호. playFrom 포함 ~ playTo 미포함 */
	List<ReservationRow> search(@Param("keyword") String keyword, @Param("status") String status,
			@Param("courseId") Long courseId, @Param("memberId") Long memberId,
			@Param("playFrom") LocalDateTime playFrom, @Param("playTo") LocalDateTime playTo,
			@Param("offset") int offset, @Param("size") int size);

	long count(@Param("keyword") String keyword, @Param("status") String status, @Param("courseId") Long courseId,
			@Param("memberId") Long memberId, @Param("playFrom") LocalDateTime playFrom,
			@Param("playTo") LocalDateTime playTo);
}
