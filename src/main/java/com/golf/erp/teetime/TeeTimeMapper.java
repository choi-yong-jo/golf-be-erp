package com.golf.erp.teetime;

import com.golf.erp.teetime.dto.TeeTimeBoardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TeeTimeMapper {
	/** 티타임 + 활성 예약(있으면) 현황 */
	List<TeeTimeBoardRow> selectBoard(@Param("courseId") Long courseId, @Param("from") LocalDate from,
			@Param("to") LocalDate to, @Param("status") String status);

	/** 이미 있는 슬롯(ux_tee_time_slot)은 건너뛰고 생성한 행 수를 반환 */
	int insertIgnoreDuplicates(@Param("rows") List<TeeTime> rows);
}
