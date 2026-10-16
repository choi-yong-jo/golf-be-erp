package com.golf.erp.dashboard;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DashboardMapper {
	DashboardSummary selectSummary();

	List<RecentReservation> selectRecentReservations(int limit);
}
