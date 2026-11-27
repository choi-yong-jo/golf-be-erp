package com.golf.erp.teetime;

import com.golf.erp.common.NotFoundException;
import com.golf.erp.teetime.TeeTimeDtos.ReservationSummary;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 티타임 모듈이 SQL 로 읽고 쓰는 것: 다른 모듈 테이블(골프장·코스·휴장·예약·회원·라운드) 조회와
 * 중복 슬롯을 건너뛰는 일괄 INSERT.
 */
@Component
class TeeTimeSlotQueries {
	/** 슬롯을 점유하는 예약 상태(ux_reservation_active_tee_time 과 같은 조건) */
	static final String ACTIVE_RESERVATION = "('PENDING', 'PENDING_PAYMENT', 'PAYING', 'CONFIRMED')";

	private static final String BOARD_SELECT = "SELECT t.tee_time_id, t.golf_course_id, gc.course_name, t.course,"
			+ " t.play_date, t.start_time, t.hole_type, t.capacity, t.green_fee, t.tee_time_status, t.version,"
			+ " r.reservation_id, r.reservation_no, r.reservation_status, r.player_count,"
			+ " m.member_id, m.member_name, m.phone AS member_phone"
			+ " FROM golf.tee_time t"
			+ " LEFT JOIN golf.golf_course gc ON gc.golf_course_id = t.golf_course_id"
			+ " LEFT JOIN golf.reservation r ON r.tee_time_id = t.tee_time_id AND r.reservation_status IN "
			+ ACTIVE_RESERVATION
			+ " LEFT JOIN golf.member m ON m.member_id = r.member_id";

	private final JdbcTemplate jdbc;

	TeeTimeSlotQueries(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 골프장 운영 정보 */
	record GolfCourseInfo(long id, String name, String status, boolean active) {
		boolean operating() {
			return active && "OPEN".equals(status);
		}
	}

	/** 휴장 구간 [start, end) */
	record ClosurePeriod(LocalDateTime start, LocalDateTime end, String reason) {
		boolean contains(LocalDateTime at) {
			return !at.isBefore(start) && at.isBefore(end);
		}
	}

	/** 일괄 INSERT 할 슬롯 */
	record NewSlot(LocalDate playDate, LocalTime startTime, BigDecimal greenFee) {
	}

	/** 예약 현황: 기간의 티타임과 활성 예약 */
	List<TeeTimeResponse> board(Long golfCourseId, String course, LocalDate from, LocalDate to,
			TeeTimeStatus status) {
		StringBuilder sql = new StringBuilder(BOARD_SELECT).append(" WHERE t.play_date BETWEEN ? AND ?");
		List<Object> args = new ArrayList<>(List.of(from, to));
		if (golfCourseId != null) {
			sql.append(" AND t.golf_course_id = ?");
			args.add(golfCourseId);
		}
		if (course != null) {
			sql.append(" AND t.course = ?");
			args.add(course);
		}
		if (status != null) {
			sql.append(" AND t.tee_time_status = ?");
			args.add(status.name());
		}
		sql.append(" ORDER BY t.play_date, t.start_time, t.golf_course_id, t.course NULLS FIRST");
		return jdbc.query(sql.toString(), (rs, i) -> boardRow(rs), args.toArray());
	}

	/** 티타임을 점유한 활성 예약(있으면) */
	Optional<ReservationSummary> activeReservation(long teeTimeId) {
		List<ReservationSummary> rows = jdbc.query("SELECT r.reservation_id, r.reservation_no, r.reservation_status,"
				+ " r.player_count, m.member_id, m.member_name, m.phone AS member_phone"
				+ " FROM golf.reservation r LEFT JOIN golf.member m ON m.member_id = r.member_id"
				+ " WHERE r.tee_time_id = ? AND r.reservation_status IN " + ACTIVE_RESERVATION
				+ " ORDER BY r.reservation_id DESC LIMIT 1", (rs, i) -> reservation(rs), teeTimeId);
		return rows.stream().findFirst();
	}

	/** 예약(취소 포함)·예약 변경 이력·라운드 중 하나라도 이 티타임을 참조하는지 */
	boolean isReferenced(long teeTimeId) {
		Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM golf.reservation WHERE tee_time_id = ?)"
				+ " OR EXISTS (SELECT 1 FROM golf.reservation_history"
				+ " WHERE before_tee_time_id = ? OR after_tee_time_id = ?)"
				+ " OR EXISTS (SELECT 1 FROM golf.round WHERE tee_time_id = ?)", Boolean.class, teeTimeId, teeTimeId,
				teeTimeId, teeTimeId);
		return Boolean.TRUE.equals(found);
	}

	GolfCourseInfo golfCourse(long golfCourseId) {
		List<GolfCourseInfo> rows = jdbc.query(
				"SELECT golf_course_id, course_name, course_status, is_active FROM golf.golf_course"
						+ " WHERE golf_course_id = ?",
				(rs, i) -> new GolfCourseInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)),
				golfCourseId);
		if (rows.isEmpty())
			throw NotFoundException.of("골프장", golfCourseId);
		return rows.get(0);
	}

	String golfCourseName(Long golfCourseId) {
		if (golfCourseId == null)
			return null;
		List<String> rows = jdbc.queryForList("SELECT course_name FROM golf.golf_course WHERE golf_course_id = ?",
				String.class, golfCourseId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** 골프장의 활성 코스 코드(course.course_code = tee_time.course) */
	Set<String> activeCourseCodes(long golfCourseId) {
		return new HashSet<>(jdbc.queryForList(
				"SELECT course_code FROM golf.course WHERE golf_course_id = ? AND is_active = TRUE", String.class,
				golfCourseId));
	}

	/** [from, to) 와 겹치는 활성 휴장 */
	List<ClosurePeriod> activeClosures(long golfCourseId, LocalDateTime from, LocalDateTime to) {
		return jdbc.query("SELECT closure_start, closure_end, reason FROM golf.golf_course_closure"
				+ " WHERE golf_course_id = ? AND is_active = TRUE AND closure_start < ? AND closure_end > ?"
				+ " ORDER BY closure_start",
				(rs, i) -> new ClosurePeriod(rs.getObject(1, LocalDateTime.class), rs.getObject(2, LocalDateTime.class),
						rs.getString(3)),
				golfCourseId, to, from);
	}

	/**
	 * 같은 골프장·코스·설정으로 슬롯 여러 개를 한 문장으로 넣는다. ux_tee_time_slot 에 걸리는 기존 슬롯은
	 * ON CONFLICT DO NOTHING 으로 건너뛰며, 실제로 들어간 행 수를 돌려준다.
	 */
	int insertIgnoringDuplicates(long golfCourseId, String course, String holeType, short capacity,
			List<NewSlot> slots) {
		if (slots.isEmpty())
			return 0;
		String[] dates = new String[slots.size()];
		String[] times = new String[slots.size()];
		String[] fees = new String[slots.size()];
		for (int i = 0; i < slots.size(); i++) {
			NewSlot s = slots.get(i);
			dates[i] = s.playDate().toString();
			times[i] = s.startTime().toString();
			fees[i] = s.greenFee() == null ? null : s.greenFee().toPlainString();
		}
		String sql = "INSERT INTO golf.tee_time"
				+ " (golf_course_id, course, play_date, start_time, hole_type, capacity, green_fee, tee_time_status, version)"
				+ " SELECT ?, ?, CAST(x.d AS date), CAST(x.t AS time), ?, ?, CAST(x.f AS numeric(12,2)), 'OPEN', 0"
				+ " FROM unnest(CAST(? AS text[]), CAST(? AS text[]), CAST(? AS text[])) AS x(d, t, f)"
				+ " ON CONFLICT DO NOTHING";
		return jdbc.update(con -> {
			PreparedStatement ps = con.prepareStatement(sql);
			ps.setLong(1, golfCourseId);
			if (course == null)
				ps.setNull(2, Types.VARCHAR);
			else
				ps.setString(2, course);
			ps.setString(3, holeType);
			ps.setShort(4, capacity);
			Array d = con.createArrayOf("text", dates);
			Array t = con.createArrayOf("text", times);
			Array f = con.createArrayOf("text", fees);
			ps.setArray(5, d);
			ps.setArray(6, t);
			ps.setArray(7, f);
			return ps;
		});
	}

	private static TeeTimeResponse boardRow(ResultSet rs) throws SQLException {
		ReservationSummary reservation = rs.getObject("reservation_id") == null ? null : reservation(rs);
		return new TeeTimeResponse(rs.getLong("tee_time_id"), rs.getObject("golf_course_id", Long.class),
				rs.getString("course_name"), rs.getString("course"), rs.getObject("play_date", LocalDate.class),
				rs.getObject("start_time", LocalTime.class), rs.getString("hole_type"), rs.getShort("capacity"),
				rs.getBigDecimal("green_fee"), TeeTimeStatus.valueOf(rs.getString("tee_time_status")),
				rs.getLong("version"), reservation);
	}

	private static ReservationSummary reservation(ResultSet rs) throws SQLException {
		return new ReservationSummary(rs.getLong("reservation_id"), rs.getString("reservation_no"),
				rs.getString("reservation_status"), rs.getObject("player_count", Integer.class),
				rs.getObject("member_id", Long.class), rs.getString("member_name"), rs.getString("member_phone"));
	}
}
