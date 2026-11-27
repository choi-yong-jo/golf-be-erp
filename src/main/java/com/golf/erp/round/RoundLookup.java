package com.golf.erp.round;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 라운드·정산이 읽어야 하는 다른 모듈 테이블(코스·홀·회원·티타임·캐디·카트)을 SQL 로 필요한 컬럼만 읽는다.
 * 다른 모듈의 엔티티를 import 하지 않기 위한 것이다.
 */
@Component
public class RoundLookup {
	private final NamedParameterJdbcTemplate jdbc;

	public RoundLookup(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** usable: course_status = OPEN 이고 is_active */
	public record CourseInfo(long id, long golfCourseId, String code, String name, String status, boolean usable) {
	}

	public record HoleInfo(long id, long courseId, short holeNo, short par, Short handicap) {
	}

	public record MemberInfo(long id, String name, String phone, String status) {
	}

	/** holes: tee_time.hole_type 에서 읽은 홀 수(해석할 수 없으면 null) */
	public record TeeTimeInfo(long id, Long golfCourseId, LocalDate playDate, LocalTime startTime, Short holes,
			BigDecimal greenFee) {
	}

	/** 캐디·카트. label: 화면 표시용(캐디 "번호 이름", 카트 "번호") */
	public record ResourceInfo(long id, long golfCourseId, String label, String status) {
	}

	public Optional<CourseInfo> course(long courseId) {
		return jdbc.query("SELECT course_id, golf_course_id, course_code, course_name, course_status, is_active"
				+ " FROM golf.course WHERE course_id = :id", Map.of("id", courseId),
				(rs, i) -> new CourseInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
						rs.getString(5), "OPEN".equals(rs.getString(5)) && rs.getBoolean(6)))
				.stream().findFirst();
	}

	/** course_id → course_name */
	public Map<Long, String> courseNames(Collection<Long> courseIds) {
		Map<Long, String> m = new HashMap<>();
		if (courseIds.isEmpty())
			return m;
		jdbc.query("SELECT course_id, course_name FROM golf.course WHERE course_id IN (:ids)",
				Map.of("ids", courseIds), rs -> {
					m.put(rs.getLong(1), rs.getString(2));
				});
		return m;
	}

	/** 그 코스의 홀이면 돌려준다(다른 코스의 홀이면 empty) */
	public Optional<HoleInfo> holeOfCourse(long holeId, long courseId) {
		return jdbc.query("SELECT hole_id, course_id, hole_no, par, handicap FROM golf.hole"
				+ " WHERE hole_id = :holeId AND course_id = :courseId",
				new MapSqlParameterSource("holeId", holeId).addValue("courseId", courseId), (rs, i) -> hole(rs))
				.stream().findFirst();
	}

	/** course_id → 홀 목록(홀 번호 순) */
	public Map<Long, List<HoleInfo>> holesOfCourses(Collection<Long> courseIds) {
		Map<Long, List<HoleInfo>> m = new LinkedHashMap<>();
		if (courseIds.isEmpty())
			return m;
		jdbc.query("SELECT hole_id, course_id, hole_no, par, handicap FROM golf.hole WHERE course_id IN (:ids)"
				+ " ORDER BY course_id, hole_no", Map.of("ids", courseIds), (rs, i) -> hole(rs))
				.forEach(h -> m.computeIfAbsent(h.courseId(), k -> new java.util.ArrayList<>()).add(h));
		return m;
	}

	public Optional<MemberInfo> member(long memberId) {
		return jdbc.query("SELECT member_id, member_name, phone, member_status FROM golf.member WHERE member_id = :id",
				Map.of("id", memberId),
				(rs, i) -> new MemberInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
				.stream().findFirst();
	}

	public Optional<TeeTimeInfo> teeTime(long teeTimeId) {
		return jdbc.query("SELECT tee_time_id, golf_course_id, play_date, start_time, hole_type, green_fee"
				+ " FROM golf.tee_time WHERE tee_time_id = :id", Map.of("id", teeTimeId),
				(rs, i) -> new TeeTimeInfo(rs.getLong(1), rs.getObject(2, Long.class),
						rs.getObject(3, LocalDate.class), rs.getObject(4, LocalTime.class), parseHoles(rs.getString(5)),
						rs.getBigDecimal(6)))
				.stream().findFirst();
	}

	public Optional<ResourceInfo> caddie(long caddieId) {
		return jdbc.query("SELECT caddie_id, golf_course_id, caddie_no || ' ' || caddie_name, caddie_status"
				+ " FROM golf.caddie WHERE caddie_id = :id", Map.of("id", caddieId), (rs, i) -> resource(rs))
				.stream().findFirst();
	}

	public Optional<ResourceInfo> cart(long cartId) {
		return jdbc.query("SELECT cart_id, golf_course_id, cart_no, cart_status FROM golf.cart WHERE cart_id = :id",
				Map.of("id", cartId), (rs, i) -> resource(rs)).stream().findFirst();
	}

	public Optional<ResourceInfo> resource(ResourceType type, long id) {
		return type == ResourceType.CADDIE ? caddie(id) : cart(id);
	}

	/** caddie_id → "번호 이름" */
	public Map<Long, String> caddieLabels(Collection<Long> ids) {
		return labels("SELECT caddie_id, caddie_no || ' ' || caddie_name FROM golf.caddie WHERE caddie_id IN (:ids)",
				ids);
	}

	/** cart_id → 카트 번호 */
	public Map<Long, String> cartLabels(Collection<Long> ids) {
		return labels("SELECT cart_id, cart_no FROM golf.cart WHERE cart_id IN (:ids)", ids);
	}

	/**
	 * 참가자의 스코어 합계를 다시 센다. [친 홀 수, 타수, 퍼트, 친 홀들의 파 합계]. 호출 전에 스코어 변경을 flush 해야 한다.
	 */
	int[] playerTotals(long roundPlayerId) {
		return jdbc.queryForObject("SELECT count(*), coalesce(sum(s.strokes), 0), coalesce(sum(s.putts), 0),"
				+ " coalesce(sum(h.par), 0) FROM golf.score s JOIN golf.hole h ON h.hole_id = s.hole_id"
				+ " WHERE s.round_player_id = :id", Map.of("id", roundPlayerId),
				(rs, i) -> new int[] { rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4) });
	}

	private Map<Long, String> labels(String sql, Collection<Long> ids) {
		Map<Long, String> m = new HashMap<>();
		if (ids.isEmpty())
			return m;
		jdbc.query(sql, Map.of("ids", ids), rs -> {
			m.put(rs.getLong(1), rs.getString(2));
		});
		return m;
	}

	private static HoleInfo hole(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new HoleInfo(rs.getLong(1), rs.getLong(2), rs.getShort(3), rs.getShort(4),
				rs.getObject(5) == null ? null : rs.getShort(5));
	}

	private static ResourceInfo resource(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new ResourceInfo(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4));
	}

	/** tee_time.hole_type("9H", "18H", "27H", "36H" 등)에서 숫자만 읽는다. */
	static Short parseHoles(String holeType) {
		if (holeType == null)
			return null;
		String digits = holeType.replaceAll("[^0-9]", "");
		if (digits.isEmpty() || digits.length() > 2)
			return null;
		short h = Short.parseShort(digits);
		return h == 9 || h == 18 || h == 27 || h == 36 ? h : null;
	}
}
