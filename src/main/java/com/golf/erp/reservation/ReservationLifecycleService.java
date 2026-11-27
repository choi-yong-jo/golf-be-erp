package com.golf.erp.reservation;

import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.port.ReservationLifecycle;
import com.golf.erp.reservation.ReservationSql.MemberRow;
import com.golf.erp.reservation.ReservationSql.TeeTimeRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 라운드 모듈용 예약 연동(체크인 대상 조회, 라운드 종료 시 완료 처리) */
@Service
@Transactional
public class ReservationLifecycleService implements ReservationLifecycle {
	private final ReservationService reservations;
	private final ReservationPlayerRepository players;
	private final ReservationSql sql;
	private final Clock clock;

	public ReservationLifecycleService(ReservationService reservations, ReservationPlayerRepository players,
			ReservationSql sql, Clock clock) {
		this.reservations = reservations;
		this.players = players;
		this.sql = sql;
		this.clock = clock;
	}

	/**
	 * 예약 행을 잠그고(체크인 중 취소·결제와 직렬화) 체크인 가능 상태(PENDING·CONFIRMED)인지 확인한다. 플레이어는
	 * reservation_player 기준, 없으면 예약 회원 한 명. 1인 그린피는 총액 / 인원(할인 반영), 총액이 없으면 green_fee_amount.
	 */
	@Override
	public CheckInTarget checkInTarget(long reservationId) {
		Reservation r = reservations.lock(reservationId);
		r.requireCheckInable();
		TeeTimeRow tee = r.getTeeTimeId() == null ? null : sql.teeTime(r.getTeeTimeId()).orElse(null);

		List<Player> list = players.findByReservationIdOrderByPlayerOrder(reservationId).stream()
				.map(p -> new Player(p.getPlayerOrder(), p.getPlayerName(), p.getPhone(), p.getMemberId())).toList();
		if (list.isEmpty()) {
			MemberRow m = sql.member(r.getMemberId()).orElseThrow(() -> NotFoundException.of("회원", r.getMemberId()));
			list = List.of(new Player(1, m.name(), m.phone(), m.id()));
		}
		return new CheckInTarget(r.getId(), r.getReservationNo(), r.getMemberId(), r.getTeeTimeId(),
				ReservationQueryService.effectiveCourseId(r, tee), ReservationQueryService.effectivePlayAt(r, tee),
				r.getPlayerCount(), perPlayerFee(r), sql.paidAmount(reservationId), list);
	}

	/** 라운드 종료 → COMPLETED. 이미 종료 상태(COMPLETED·CANCELLED·NO_SHOW·FAILED)면 아무것도 하지 않는다. */
	@Override
	public void markCompleted(long reservationId) {
		Reservation r = reservations.lock(reservationId);
		ReservationStatus before = r.getStatus();
		LocalDateTime now = LocalDateTime.now(clock);
		if (r.completeByRound(now))
			reservations.history("COMPLETE", r, before, "라운드 종료", now);
	}

	private static BigDecimal perPlayerFee(Reservation r) {
		if (r.getTotalAmount() != null && r.getPlayerCount() != null && r.getPlayerCount() > 0)
			return r.getTotalAmount().divide(BigDecimal.valueOf(r.getPlayerCount()), 2, RoundingMode.HALF_UP);
		return r.getGreenFeeAmount();
	}
}
