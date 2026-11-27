package com.golf.erp.reservation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 예약 처리에 필요한 다른 테이블(member, reservation_restriction, tee_time, golf_course, golf_course_closure, payment,
 * round) 읽기·쓰기. 다른 모듈 엔티티를 import 하지 않으려고 필요한 컬럼만 SQL 로 다룬다.
 */
@Component
class ReservationSql {
	/** ERP 현장 결제의 pg_provider */
	static final String ERP_PROVIDER = "ERP";

	private final JdbcTemplate jdbc;

	ReservationSql(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// ---------------------------------------------------------------- member

	record MemberRow(long id, String name, String phone, String status) {
	}

	/** 회원 행을 잠근다(예약 앱과 같은 방식으로 한 회원의 예약 생성을 직렬화). */
	Optional<MemberRow> lockMember(long memberId) {
		return jdbc.query("SELECT member_id, member_name, phone, member_status FROM golf.member WHERE member_id = ?"
				+ " FOR UPDATE", MEMBER, memberId).stream().findFirst();
	}

	Optional<MemberRow> member(long memberId) {
		return jdbc.query("SELECT member_id, member_name, phone, member_status FROM golf.member WHERE member_id = ?",
				MEMBER, memberId).stream().findFirst();
	}

	record RestrictionRow(String type, String reason, LocalDateTime endsAt) {
	}

	/** at 시각에 적용 중인 예약 제한(가장 먼저 시작한 것) */
	Optional<RestrictionRow> activeRestriction(long memberId, LocalDateTime at) {
		return jdbc.query("""
				SELECT restriction_type, reason, ends_at FROM golf.reservation_restriction
				 WHERE member_id = ? AND starts_at <= ? AND (ends_at IS NULL OR ends_at > ?)
				 ORDER BY starts_at LIMIT 1""",
				(rs, i) -> new RestrictionRow(rs.getString(1), rs.getString(2), ts(rs, 3)), memberId, at, at).stream()
				.findFirst();
	}

	// ---------------------------------------------------------------- tee_time / golf_course

	record TeeTimeRow(long id, Long golfCourseId, String course, LocalDate playDate, LocalTime startTime,
			String holeType, int capacity, BigDecimal greenFee, String status) {

		LocalDateTime playAt() {
			return playDate.atTime(startTime);
		}
	}

	private static final String TEE_TIME_COLUMNS = "SELECT tee_time_id, golf_course_id, course, play_date, start_time,"
			+ " hole_type, capacity, green_fee, tee_time_status FROM golf.tee_time WHERE tee_time_id = ?";

	/** 티타임 행을 잠근다. tee_time 은 티타임 모듈 소유라 엔티티 대신 SQL 로 다룬다. */
	Optional<TeeTimeRow> lockTeeTime(long teeTimeId) {
		return jdbc.query(TEE_TIME_COLUMNS + " FOR UPDATE", TEE_TIME, teeTimeId).stream().findFirst();
	}

	Optional<TeeTimeRow> teeTime(long teeTimeId) {
		return jdbc.query(TEE_TIME_COLUMNS, TEE_TIME, teeTimeId).stream().findFirst();
	}

	/** 티타임 상태 변경. version 은 예약 앱(@Version)과 공유하므로 함께 올린다. */
	void changeTeeTimeStatus(long teeTimeId, String status) {
		jdbc.update("UPDATE golf.tee_time SET tee_time_status = ?, version = version + 1 WHERE tee_time_id = ?", status,
				teeTimeId);
	}

	record CourseRow(String name, String status, boolean active) {
		boolean operating() {
			return active && "OPEN".equals(status);
		}
	}

	Optional<CourseRow> golfCourse(long golfCourseId) {
		return jdbc.query("SELECT course_name, course_status, is_active FROM golf.golf_course WHERE golf_course_id = ?",
				(rs, i) -> new CourseRow(rs.getString(1), rs.getString(2), rs.getBoolean(3)), golfCourseId).stream()
				.findFirst();
	}

	String golfCourseName(Long golfCourseId) {
		return golfCourseId == null ? null : golfCourse(golfCourseId).map(CourseRow::name).orElse(null);
	}

	/** at 시각이 포함되는 활성 휴장([closure_start, closure_end))의 사유 */
	Optional<String> activeClosureReason(long golfCourseId, LocalDateTime at) {
		return jdbc.queryForList("""
				SELECT reason FROM golf.golf_course_closure
				 WHERE golf_course_id = ? AND is_active AND closure_start <= ? AND closure_end > ?
				 ORDER BY closure_start LIMIT 1""", String.class, golfCourseId, at, at).stream().findFirst();
	}

	// ---------------------------------------------------------------- payment

	record PaymentRow(long id, String type, String status, String method, String pgProvider, String pgTransactionId,
			BigDecimal amount, LocalDateTime requestedAt, LocalDateTime completedAt, LocalDateTime refundedAt,
			String failureReason) {

		boolean paid() {
			return "PAYMENT".equals(type) && ("PAID".equals(status) || "SUCCEEDED".equals(status));
		}

		boolean erp() {
			return ERP_PROVIDER.equals(pgProvider);
		}
	}

	List<PaymentRow> payments(long reservationId) {
		return jdbc.query("""
				SELECT payment_id, payment_type, payment_status, payment_method, pg_provider, pg_transaction_id, amount,
				       requested_at, completed_at, refunded_at, failure_reason
				  FROM golf.payment WHERE reservation_id = ? ORDER BY requested_at, payment_id""",
				(rs, i) -> new PaymentRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6), rs.getBigDecimal(7), ts(rs, 8), ts(rs, 9), ts(rs, 10),
						rs.getString(11)),
				reservationId);
	}

	/**
	 * ERP 현장 결제(PAID). ck_payment_paid 때문에 completed_at 과 pg_transaction_id 를 반드시 채운다
	 * (pg_provider=ERP, 거래 번호 ERP-{uuid}).
	 */
	long insertErpPayment(long reservationId, String method, BigDecimal amount, LocalDateTime now) {
		Long id = jdbc.queryForObject("""
				INSERT INTO golf.payment (reservation_id, payment_type, payment_status, payment_method, pg_provider,
				                          pg_transaction_id, amount, requested_at, completed_at)
				VALUES (?, 'PAYMENT', 'PAID', ?, ?, ?, ?, ?, ?)
				RETURNING payment_id""", Long.class, reservationId, method, ERP_PROVIDER,
				ERP_PROVIDER + "-" + UUID.randomUUID(), amount, now, now);
		return id == null ? 0 : id;
	}

	/** ERP 결제 환불: 예약 앱과 같이 같은 행을 REFUNDED 로 바꾼다(별도 REFUND 행을 만들지 않음). */
	int refundErpPayment(long paymentId, LocalDateTime now) {
		return jdbc.update("""
				UPDATE golf.payment SET payment_status = 'REFUNDED', refunded_at = ?
				 WHERE payment_id = ? AND pg_provider = ? AND payment_status IN ('PAID', 'SUCCEEDED')""", now, paymentId,
				ERP_PROVIDER);
	}

	/**
	 * 예약 단계에서 결제된 금액: 완료된 PAYMENT 행 합계 - 완료된 REFUND 행 합계(예약 앱의 부분 환불). 같은 행을 REFUNDED 로 바꾼
	 * 환불은 PAID 에서 빠지므로 자동으로 제외된다. 0 미만이면 0.
	 */
	BigDecimal paidAmount(long reservationId) {
		BigDecimal v = jdbc.queryForObject("""
				SELECT COALESCE(SUM(CASE WHEN payment_type = 'PAYMENT' AND payment_status IN ('PAID', 'SUCCEEDED')
				                         THEN amount
				                         WHEN payment_type = 'REFUND' AND payment_status IN ('PAID', 'SUCCEEDED', 'REFUNDED')
				                         THEN -amount
				                         ELSE 0 END), 0)
				  FROM golf.payment WHERE reservation_id = ?""", BigDecimal.class, reservationId);
		return v == null || v.signum() < 0 ? BigDecimal.ZERO : v;
	}

	// ---------------------------------------------------------------- round

	/** 이 예약으로 체크인한(취소되지 않은) 라운드 id */
	Optional<Long> activeRoundId(long reservationId) {
		return jdbc.queryForList("SELECT round_id FROM golf.round WHERE reservation_id = ? AND round_status <> 'CANCELLED'"
				+ " ORDER BY round_id LIMIT 1", Long.class, reservationId).stream().findFirst();
	}

	// ---------------------------------------------------------------- mappers

	private static final RowMapper<MemberRow> MEMBER = (rs, i) -> new MemberRow(rs.getLong(1), rs.getString(2),
			rs.getString(3), rs.getString(4));

	private static final RowMapper<TeeTimeRow> TEE_TIME = (rs, i) -> new TeeTimeRow(rs.getLong(1),
			rs.getObject(2, Long.class), rs.getString(3), rs.getObject(4, LocalDate.class),
			rs.getObject(5, LocalTime.class), rs.getString(6), rs.getInt(7), rs.getBigDecimal(8), rs.getString(9));

	static LocalDateTime ts(ResultSet rs, int col) throws SQLException {
		return rs.getObject(col, LocalDateTime.class);
	}
}
