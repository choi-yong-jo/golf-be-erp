package com.golf.erp.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** 골프장 이름 조회. 골프장 모듈 엔티티를 참조하지 않으려고 SQL 로 읽는다. */
@Component
class GolfCourseNameLookup {
	private final NamedParameterJdbcTemplate jdbc;

	GolfCourseNameLookup(JdbcTemplate jdbc) {
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	/** golf_course_id → course_name */
	Map<Long, String> names(Collection<Long> golfCourseIds) {
		Map<Long, String> m = new HashMap<>();
		if (golfCourseIds == null || golfCourseIds.isEmpty())
			return m;
		jdbc.query("SELECT golf_course_id, course_name FROM golf.golf_course WHERE golf_course_id IN (:ids)",
				Map.of("ids", golfCourseIds), (RowCallbackHandler) rs -> {
					m.put(rs.getLong(1), rs.getString(2));
				});
		return m;
	}
}
