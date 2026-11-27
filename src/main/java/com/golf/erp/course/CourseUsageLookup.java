package com.golf.erp.course;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 다른 모듈 테이블(tee_time, reservation)에서 코스·휴장 관련 사용 현황을 SQL 로 읽는다. */
@Component
class CourseUsageLookup {
	/** 슬롯을 점유하는 예약 상태(ux_reservation_active_tee_time 과 같은 조건) */
	private static final String ACTIVE_RESERVATION = "('PENDING', 'PENDING_PAYMENT', 'PAYING', 'CONFIRMED')";

	private final JdbcTemplate jdbc;

	CourseUsageLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 골프장의 [from, to) 플레이 일시에 걸린 활성 예약 수 (티타임 예약과 일시 직접 지정 예약 모두) */
	int activeReservationsBetween(long golfCourseId, LocalDateTime from, LocalDateTime to) {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM golf.reservation r"
				+ " LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id"
				+ " WHERE r.reservation_status IN " + ACTIVE_RESERVATION
				+ " AND COALESCE(t.golf_course_id, r.golf_course_id) = ?"
				+ " AND COALESCE(t.play_date + t.start_time, r.play_at) >= ?"
				+ " AND COALESCE(t.play_date + t.start_time, r.play_at) < ?", Integer.class, golfCourseId, from, to);
		return n == null ? 0 : n;
	}

	/** 이 골프장에서 course 값으로 이 코스 코드를 쓰는 티타임이 있는지 */
	boolean teeTimeUsesCourseCode(long golfCourseId, String courseCode) {
		Boolean found = jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM golf.tee_time WHERE golf_course_id = ? AND course = ?)", Boolean.class,
				golfCourseId, courseCode);
		return Boolean.TRUE.equals(found);
	}
}
