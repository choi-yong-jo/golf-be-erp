package com.golf.erp.rental;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/** 다른 모듈 테이블(회원·예약·라운드)에서 렌탈 접수에 필요한 컬럼만 SQL 로 읽는다. */
@Component
class RentalLookup {
	private final JdbcTemplate jdbc;

	RentalLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	record Contact(String name, String phone) {
	}

	Optional<Contact> member(long memberId) {
		return jdbc.query("SELECT member_name, phone FROM golf.member WHERE member_id = ?",
				(rs, n) -> new Contact(rs.getString(1), rs.getString(2)), memberId).stream().findFirst();
	}

	/** 라운드의 골프장(라운드가 없으면 empty) */
	Optional<Long> roundGolfCourseId(long roundId) {
		return jdbc.query("SELECT golf_course_id FROM golf.round WHERE round_id = ?", (rs, n) -> rs.getLong(1),
				roundId).stream().findFirst();
	}

	/** 예약의 골프장. 예약에 직접 없으면 티타임의 골프장(둘 다 없으면 empty) */
	Optional<Long> reservationGolfCourseId(long reservationId) {
		return jdbc.query("SELECT COALESCE(r.golf_course_id, t.golf_course_id) FROM golf.reservation r"
				+ " LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id WHERE r.reservation_id = ?",
				(rs, n) -> (Long) rs.getObject(1, Long.class), reservationId).stream()
				.filter(Objects::nonNull).findFirst();
	}
}
