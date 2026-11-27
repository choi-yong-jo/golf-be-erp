package com.golf.erp.resource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 지금 라운드에 배정 중인(round_assignment.released_at IS NULL) 캐디·카트. 라운드 모듈 엔티티를 참조하지 않으려고 SQL 로 읽는다. */
@Component
class AssignmentLookup {
	private final JdbcTemplate jdbc;

	AssignmentLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** caddie_id → round_id */
	Map<Long, Long> activeCaddies() {
		return query("caddie_id");
	}

	/** cart_id → round_id */
	Map<Long, Long> activeCarts() {
		return query("cart_id");
	}

	private Map<Long, Long> query(String column) {
		Map<Long, Long> m = new HashMap<>();
		jdbc.query("SELECT " + column + ", round_id FROM golf.round_assignment WHERE released_at IS NULL AND " + column
				+ " IS NOT NULL", rs -> {
					m.put(rs.getLong(1), rs.getLong(2));
				});
		return m;
	}
}
