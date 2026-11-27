package com.golf.erp.dashboard;

import com.golf.erp.dashboard.DashboardDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 운영 현황 요약. 여러 모듈의 테이블을 읽으므로 모두 SQL 로 집계한다. 날짜는 Asia/Seoul 기준으로 애플리케이션이 넘긴다. */
@Service
@Transactional(readOnly = true)
public class DashboardService {
	private static final List<String> RESERVATION_STATUSES = List.of("PENDING", "PENDING_PAYMENT", "PAYING",
			"CONFIRMED", "REFUND_PENDING", "CANCELLED", "COMPLETED", "NO_SHOW", "FAILED");
	private static final String PLAY_AT = "COALESCE(r.play_at, t.play_date + t.start_time)";

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public DashboardService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public DashboardSummary summary(LocalDate date, int recentLimit) {
		LocalDate day = date == null ? LocalDate.now(clock) : date;
		LocalDateTime from = day.atStartOfDay();
		LocalDateTime to = day.plusDays(1).atStartOfDay();
		return new DashboardSummary(day, reservations(from, to), teeTimes(day), rounds(day), revenue(from, to),
				count("SELECT COUNT(*) FROM golf.reservation"
						+ " WHERE reservation_status IN ('PENDING', 'PENDING_PAYMENT', 'PAYING')"),
				count("SELECT COUNT(*) FROM golf.member WHERE member_status = 'ACTIVE'"), recent(recentLimit));
	}

	/** 최근 등록된 예약(예약 앱 예약 포함) */
	public List<RecentReservation> recent(int limit) {
		if (limit < 1 || limit > 50)
			throw new IllegalArgumentException("limit 은 1~50 이어야 합니다.");
		return jdbc.query("""
				SELECT r.reservation_id, r.reservation_no, m.member_name, gc.course_name, %s AS play_at,
				       r.player_count, r.reservation_status, r.reserved_at
				  FROM golf.reservation r
				  JOIN golf.member m ON m.member_id = r.member_id
				  LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id
				  LEFT JOIN golf.golf_course gc ON gc.golf_course_id = COALESCE(r.golf_course_id, t.golf_course_id)
				 ORDER BY r.created_at DESC, r.reservation_id DESC
				 LIMIT ?""".formatted(PLAY_AT),
				(rs, i) -> new RecentReservation(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getObject(5, LocalDateTime.class), rs.getInt(6), rs.getString(7),
						rs.getObject(8, LocalDateTime.class)),
				limit);
	}

	private ReservationStats reservations(LocalDateTime from, LocalDateTime to) {
		Map<String, Long> byStatus = new LinkedHashMap<>();
		RESERVATION_STATUSES.forEach(s -> byStatus.put(s, 0L));
		long[] players = { 0 };
		jdbc.query("""
				SELECT r.reservation_status, COUNT(*), COALESCE(SUM(r.player_count), 0)
				  FROM golf.reservation r
				  LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id
				 WHERE %1$s >= ? AND %1$s < ?
				 GROUP BY r.reservation_status""".formatted(PLAY_AT), rs -> {
			String status = rs.getString(1);
			byStatus.put(status, rs.getLong(2));
			if (!"CANCELLED".equals(status) && !"FAILED".equals(status) && !"NO_SHOW".equals(status))
				players[0] += rs.getLong(3);
		}, from, to);
		long total = byStatus.entrySet().stream()
				.filter(e -> !"CANCELLED".equals(e.getKey()) && !"FAILED".equals(e.getKey()))
				.mapToLong(Map.Entry::getValue).sum();
		return new ReservationStats(total, players[0], byStatus);
	}

	private TeeTimeStats teeTimes(LocalDate day) {
		Map<String, Long> m = groupCount(
				"SELECT tee_time_status, COUNT(*) FROM golf.tee_time WHERE play_date = ? GROUP BY tee_time_status", day);
		long open = m.getOrDefault("OPEN", 0L);
		long booked = m.getOrDefault("BOOKED", 0L);
		long operating = open + booked;
		BigDecimal rate = operating == 0 ? BigDecimal.ZERO.setScale(1)
				: BigDecimal.valueOf(booked * 100).divide(BigDecimal.valueOf(operating), 1, RoundingMode.HALF_UP);
		return new TeeTimeStats(m.values().stream().mapToLong(Long::longValue).sum(), open, booked,
				m.getOrDefault("BLOCKED", 0L), m.getOrDefault("CLOSED", 0L), rate);
	}

	private RoundStats rounds(LocalDate day) {
		Map<String, Long> m = groupCount(
				"SELECT round_status, COUNT(*) FROM golf.round WHERE play_date = ? GROUP BY round_status", day);
		long checkedIn = m.getOrDefault("CHECKED_IN", 0L);
		long playing = m.getOrDefault("PLAYING", 0L);
		return new RoundStats(checkedIn, playing, m.getOrDefault("FINISHED", 0L), m.getOrDefault("CANCELLED", 0L),
				checkedIn + playing);
	}

	private Revenue revenue(LocalDateTime from, LocalDateTime to) {
		BigDecimal reservation = sum("""
				SELECT COALESCE(SUM(amount), 0) FROM golf.payment
				 WHERE payment_type = 'PAYMENT' AND payment_status IN ('PAID', 'SUCCEEDED')
				   AND completed_at >= ? AND completed_at < ?""", from, to);
		BigDecimal round = sum("""
				SELECT COALESCE(SUM(amount), 0) FROM golf.round_payment
				 WHERE payment_status = 'PAID' AND payment_method <> 'PREPAID'
				   AND paid_at >= ? AND paid_at < ?""", from, to);
		return new Revenue(reservation, round, reservation.add(round));
	}

	private Map<String, Long> groupCount(String sql, Object... args) {
		Map<String, Long> m = new HashMap<>();
		jdbc.query(sql, rs -> {
			m.put(rs.getString(1), rs.getLong(2));
		}, args);
		return m;
	}

	private long count(String sql) {
		Long v = jdbc.queryForObject(sql, Long.class);
		return v == null ? 0 : v;
	}

	private BigDecimal sum(String sql, Object... args) {
		BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
		return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP);
	}
}
