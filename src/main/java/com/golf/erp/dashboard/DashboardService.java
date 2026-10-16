package com.golf.erp.dashboard;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DashboardService {
	private final DashboardMapper mapper;

	public DashboardService(DashboardMapper mapper) {
		this.mapper = mapper;
	}

	public DashboardSummary summary() {
		return mapper.selectSummary();
	}

	public List<RecentReservation> recent(int limit) {
		if (limit < 1 || limit > 100)
			throw new IllegalArgumentException("limit은 1~100 사이여야 합니다.");
		return mapper.selectRecentReservations(limit);
	}
}
