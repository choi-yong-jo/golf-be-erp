package com.golf.erp.member;

import com.golf.erp.common.PageResult;
import com.golf.erp.member.MemberDtos.MemberReservationRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 회원의 예약 내역. 예약 모듈 엔티티를 참조하지 않으려고 SQL 로 읽는다(예약 앱이 만든 예약 포함). */
@Component
class MemberReservationQuery {
	/** 예약 앱은 tee_time_id 만, 다른 시스템은 golf_course_id + play_at 만 채울 수 있어 둘 다 본다. */
	private static final String FROM = """
			  FROM golf.reservation r
			  LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id
			  LEFT JOIN golf.golf_course gc ON gc.golf_course_id = COALESCE(r.golf_course_id, t.golf_course_id)
			 WHERE r.member_id = ?""";

	private final JdbcTemplate jdbc;

	MemberReservationQuery(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	PageResult<MemberReservationRow> find(long memberId, String status, int page, int size) {
		List<Object> args = new ArrayList<>();
		args.add(memberId);
		String where = FROM;
		if (status != null) {
			where += " AND r.reservation_status = ?";
			args.add(status);
		}
		Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
		args.add(size);
		args.add((long) page * size);
		List<MemberReservationRow> items = jdbc.query("""
				SELECT r.reservation_id, r.reservation_no, r.tee_time_id,
				       COALESCE(r.golf_course_id, t.golf_course_id) AS golf_course_id, gc.course_name,
				       COALESCE(r.play_at, t.play_date + t.start_time) AS play_at, r.player_count,
				       r.reservation_status, r.total_amount, r.reserved_at, r.cancelled_at""" + where + """

				 ORDER BY play_at DESC NULLS LAST, r.reservation_id DESC
				 LIMIT ? OFFSET ?""", (rs, i) -> new MemberReservationRow(rs.getLong("reservation_id"),
				rs.getString("reservation_no"), rs.getObject("tee_time_id", Long.class),
				rs.getObject("golf_course_id", Long.class), rs.getString("course_name"),
				toLocal(rs.getTimestamp("play_at")), rs.getInt("player_count"), rs.getString("reservation_status"),
				rs.getBigDecimal("total_amount"), toLocal(rs.getTimestamp("reserved_at")),
				toLocal(rs.getTimestamp("cancelled_at"))), args.toArray());
		return new PageResult<>(items, page, size, total == null ? 0 : total);
	}

	private static LocalDateTime toLocal(Timestamp ts) {
		return ts == null ? null : ts.toLocalDateTime();
	}
}
