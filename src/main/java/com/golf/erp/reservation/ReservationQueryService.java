package com.golf.erp.reservation;

import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.reservation.ReservationDtos.*;
import com.golf.erp.reservation.ReservationSql.MemberRow;
import com.golf.erp.reservation.ReservationSql.TeeTimeRow;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 예약 조회(목록·상세). 목록은 회원·티타임·골프장을 함께 보여 주므로 SQL 로 읽는다. */
@Service
@Transactional(readOnly = true)
public class ReservationQueryService {
	/** 예약 앱은 tee_time_id 를, 다른 시스템은 golf_course_id + play_at 만 채울 수 있어 둘 다 본다. */
	private static final String FROM = """
			  FROM golf.reservation r
			  JOIN golf.member m ON m.member_id = r.member_id
			  LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id
			  LEFT JOIN golf.golf_course gc ON gc.golf_course_id = COALESCE(r.golf_course_id, t.golf_course_id)
			 WHERE 1 = 1""";
	private static final String PLAY_AT = "COALESCE(r.play_at, t.play_date + t.start_time)";

	private final JdbcTemplate jdbc;
	private final ReservationRepository repo;
	private final ReservationPlayerRepository players;
	private final ReservationHistoryRepository histories;
	private final ReservationSql sql;

	public ReservationQueryService(JdbcTemplate jdbc, ReservationRepository repo, ReservationPlayerRepository players,
			ReservationHistoryRepository histories, ReservationSql sql) {
		this.jdbc = jdbc;
		this.repo = repo;
		this.players = players;
		this.histories = histories;
		this.sql = sql;
	}

	/**
	 * @param keyword 예약번호·회원 이름·전화번호 부분 일치
	 * @param from    플레이 날짜 시작(포함)
	 * @param to      플레이 날짜 끝(포함)
	 */
	public PageResult<ReservationRow> list(String keyword, ReservationStatus status, Long golfCourseId, Long memberId,
			Long teeTimeId, LocalDate from, LocalDate to, int page, int size) {
		Pages.of(page, size, Sort.unsorted());
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		StringBuilder where = new StringBuilder(FROM);
		List<Object> args = new ArrayList<>();
		if (keyword != null && !keyword.isBlank()) {
			String like = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
			where.append(" AND (LOWER(r.reservation_no) LIKE ? OR LOWER(m.member_name) LIKE ? OR m.phone LIKE ?)");
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (status != null) {
			where.append(" AND r.reservation_status = ?");
			args.add(status.name());
		}
		if (golfCourseId != null) {
			where.append(" AND COALESCE(r.golf_course_id, t.golf_course_id) = ?");
			args.add(golfCourseId);
		}
		if (memberId != null) {
			where.append(" AND r.member_id = ?");
			args.add(memberId);
		}
		if (teeTimeId != null) {
			where.append(" AND r.tee_time_id = ?");
			args.add(teeTimeId);
		}
		if (from != null) {
			where.append(" AND " + PLAY_AT + " >= ?");
			args.add(from.atStartOfDay());
		}
		if (to != null) {
			where.append(" AND " + PLAY_AT + " < ?");
			args.add(to.plusDays(1).atStartOfDay());
		}
		Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
		args.add(size);
		args.add((long) page * size);
		List<ReservationRow> items = jdbc.query("""
				SELECT r.reservation_id, r.reservation_no, r.member_id, m.member_name, m.phone,
				       COALESCE(r.golf_course_id, t.golf_course_id) AS golf_course_id, gc.course_name,
				       r.tee_time_id, t.course, %s AS effective_play_at, r.player_count, r.reservation_status,
				       r.total_amount, r.reserved_at""".formatted(PLAY_AT) + where + """

				 ORDER BY effective_play_at DESC NULLS LAST, r.reservation_id DESC
				 LIMIT ? OFFSET ?""",
				(rs, i) -> new ReservationRow(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
						rs.getString(5), rs.getObject(6, Long.class), rs.getString(7), rs.getObject(8, Long.class),
						rs.getString(9), ReservationSql.ts(rs, 10), rs.getInt(11),
						ReservationStatus.valueOf(rs.getString(12)), rs.getBigDecimal(13), ReservationSql.ts(rs, 14)),
				args.toArray());
		return new PageResult<>(items, page, size, total == null ? 0 : total);
	}

	public ReservationDetail detail(long id) {
		Reservation r = repo.findById(id).orElseThrow(() -> NotFoundException.of("예약", id));
		return detail(r);
	}

	/** 이미 읽은(같은 트랜잭션에서 변경한) 예약의 상세 */
	public ReservationDetail detail(Reservation r) {
		long id = r.getId();
		MemberRow member = sql.member(r.getMemberId()).orElse(null);
		TeeTimeRow tee = r.getTeeTimeId() == null ? null : sql.teeTime(r.getTeeTimeId()).orElse(null);
		Long courseId = effectiveCourseId(r, tee);
		LocalDateTime playAt = effectivePlayAt(r, tee);
		return new ReservationDetail(id, r.getReservationNo(), r.getStatus(), r.getMemberId(),
				member == null ? null : member.name(), member == null ? null : member.phone(), courseId,
				sql.golfCourseName(courseId), r.getTeeTimeId(), TeeTimeSummary.of(tee), playAt, r.getPlayerCount(),
				r.getGreenFeeAmount(), r.getDiscountAmount(), r.getTotalAmount(), sql.paidAmount(id), r.getMemo(),
				r.getReservedAt(), r.getConfirmedAt(), r.getCancelledAt(), r.getCancelReason(),
				r.getPaymentStartedAt(), r.getUpdatedAt(), r.getVersion(), sql.activeRoundId(id).orElse(null),
				players.findByReservationIdOrderByPlayerOrder(id).stream().map(PlayerResponse::of).toList(),
				sql.payments(id).stream().map(PaymentResponse::of).toList(),
				histories.findByReservationIdOrderByCreatedAtAscIdAsc(id).stream().map(HistoryResponse::of).toList());
	}

	static Long effectiveCourseId(Reservation r, TeeTimeRow tee) {
		return r.getGolfCourseId() != null ? r.getGolfCourseId() : tee == null ? null : tee.golfCourseId();
	}

	static LocalDateTime effectivePlayAt(Reservation r, TeeTimeRow tee) {
		return r.getPlayAt() != null ? r.getPlayAt() : tee == null ? null : tee.playAt();
	}

	private static String escapeLike(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
