package com.golf.erp.lodging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 다른 모듈 테이블(회원)에서 숙박 예약에 필요한 컬럼만 SQL 로 읽는다. */
@Component
class LodgingLookup {
	private final JdbcTemplate jdbc;

	LodgingLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	record Contact(String name, String phone) {
	}

	Optional<Contact> member(long memberId) {
		return jdbc.query("SELECT member_name, phone FROM golf.member WHERE member_id = ?",
				(rs, n) -> new Contact(rs.getString(1), rs.getString(2)), memberId).stream().findFirst();
	}
}
