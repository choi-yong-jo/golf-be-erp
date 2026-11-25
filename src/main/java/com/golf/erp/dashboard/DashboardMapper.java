package com.golf.erp.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DashboardMapper {
	DashboardSummary selectSummary(@Param("today") LocalDate today);

	List<RecentReservation> selectRecentReservations(@Param("limit") int limit);
}
