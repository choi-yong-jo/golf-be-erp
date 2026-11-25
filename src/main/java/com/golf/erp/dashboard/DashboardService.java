package com.golf.erp.dashboard;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {
	private final DashboardMapper mapper;
	private final Clock clock;

	public DashboardService(DashboardMapper mapper, Clock clock) {
		this.mapper = mapper;
		this.clock = clock;
	}

	public DashboardSummary summary() {
		return mapper.selectSummary(LocalDate.now(clock));
	}

	public List<RecentReservation> recent(int limit) {
		if (limit < 1 || limit > 100)
			throw new IllegalArgumentException("limit은 1~100 사이여야 합니다.");
		return mapper.selectRecentReservations(limit);
	}
}
