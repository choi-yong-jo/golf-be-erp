package com.golf.erp.reservation;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.port.GreenFeeQuoter;
import com.golf.erp.reservation.ReservationDtos.PlayerRequest;
import com.golf.erp.reservation.ReservationDtos.ReservationCreateRequest;
import com.golf.erp.reservation.ReservationDtos.ReservationDetail;
import com.golf.erp.reservation.ReservationSql.CourseRow;
import com.golf.erp.reservation.ReservationSql.MemberRow;
import com.golf.erp.reservation.ReservationSql.PaymentRow;
import com.golf.erp.reservation.ReservationSql.TeeTimeRow;
import com.golf.erp.security.CurrentUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP(직원) 예약 처리. 예약 앱(golf-reservation-api)과 같은 테이블을 쓰므로 그 규칙을 따른다.
 * <ul>
 * <li>동시성: 회원 행과 티타임 행을 SELECT ... FOR UPDATE 로 잠그고, 부분 유니크 인덱스(ux_reservation_active_tee_time)가
 * 최종적으로 중복 예약을 막는다. 기존 예약은 행 잠금 + @Version(예약 앱과 공유)으로 보호한다.</li>
 * <li>예약하면 티타임을 BOOKED, 취소하면 OPEN 으로 되돌린다(tee_time.version 도 올린다).</li>
 * <li>예약 앱이 결제·환불 중인 예약(PAYING, REFUND_PENDING)과 온라인(PG) 결제 건의 취소는 예약 앱에서 처리하도록 막는다.</li>
 * <li>모든 변경은 reservation_history 에 남긴다.</li>
 * </ul>
 */
@Service
@Transactional
public class ReservationService {
	private static final String NO_PREFIX = "RV";
	private static final String DEFAULT_CANCEL_REASON = "관리자 취소";

	private final ReservationRepository repo;
	private final ReservationPlayerRepository players;
	private final ReservationHistoryRepository histories;
	private final ReservationSql sql;
	private final ReservationQueryService query;
	private final ObjectProvider<GreenFeeQuoter> feeQuoter;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public ReservationService(ReservationRepository repo, ReservationPlayerRepository players,
			ReservationHistoryRepository histories, ReservationSql sql, ReservationQueryService query,
			ObjectProvider<GreenFeeQuoter> feeQuoter, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.players = players;
		this.histories = histories;
		this.sql = sql;
		this.query = query;
		this.feeQuoter = feeQuoter;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * 예약 생성(PENDING). 조건: 회원 ACTIVE·진행 중인 예약 제한 없음, 티타임 OPEN·미래·정원 이내, 골프장 운영 중·휴장 아님.
	 * 1인 그린피는 tee_time.green_fee, 없으면 요금 정책(GreenFeeQuoter).
	 */
	public ReservationDetail create(ReservationCreateRequest r) {
		List<PlayerRequest> companions = r.companions() == null ? List.of() : r.companions();
		if (companions.size() > r.playerCount() - 1)
			throw new IllegalArgumentException("동반자는 최대 " + (r.playerCount() - 1) + "명까지 입력할 수 있습니다.");
		for (PlayerRequest p : companions)
			refs.requireIfPresent(Ref.MEMBER, p.memberId());
		LocalDateTime now = now();

		// 1) 회원: 잠금 → 상태 → 예약 제한
		MemberRow member = sql.lockMember(r.memberId()).orElseThrow(() -> NotFoundException.of("회원", r.memberId()));
		if (!"ACTIVE".equals(member.status()))
			throw new ConflictException("활성 상태의 회원만 예약할 수 있습니다. (현재: " + member.status() + ")");
		sql.activeRestriction(member.id(), now).ifPresent(x -> {
			throw new ConflictException("예약이 제한된 회원입니다: " + x.type() + " - " + x.reason()
					+ (x.endsAt() == null ? " (무기한)" : " (" + x.endsAt() + " 까지)"));
		});

		// 2) 티타임: 잠금 → 상태·시각·정원
		TeeTimeRow tee = sql.lockTeeTime(r.teeTimeId()).orElseThrow(() -> NotFoundException.of("티타임", r.teeTimeId()));
		if (!"OPEN".equals(tee.status()))
			throw new ConflictException("예약할 수 없는 티타임입니다. (상태: " + tee.status() + ")");
		LocalDateTime playAt = tee.playAt();
		if (!playAt.isAfter(now))
			throw new ConflictException("이미 지난 티타임입니다.");
		if (r.playerCount() > tee.capacity())
			throw new IllegalArgumentException("인원이 티타임 정원(" + tee.capacity() + "명)을 넘습니다.");

		// 3) 골프장 운영·휴장
		Long courseId = tee.golfCourseId();
		if (courseId != null) {
			CourseRow course = sql.golfCourse(courseId).orElseThrow(() -> NotFoundException.of("골프장", courseId));
			if (!course.operating())
				throw new ConflictException("운영 중인 골프장이 아닙니다: " + course.name());
			sql.activeClosureReason(courseId, playAt).ifPresent(reason -> {
				throw new ConflictException("휴장 기간의 티타임입니다: " + reason);
			});
		}
		// 다른 시스템이 티타임 상태를 바꾸지 않고 예약했을 수도 있어 예약 행도 확인한다
		if (repo.existsByTeeTimeIdAndStatusIn(tee.id(), ReservationStatus.ACTIVE))
			throw new ConflictException("이미 예약된 티타임입니다.");

		// 4) 저장
		BigDecimal fee = greenFeePerPlayer(tee, courseId, playAt);
		Reservation reservation = new Reservation(member.id(), tee.id(), courseId, playAt, r.playerCount(), fee,
				BigDecimal.ZERO, blankToNull(r.memo()), now);
		repo.saveAndFlush(reservation);
		reservation.assignReservationNo(DocumentNo.of(NO_PREFIX, now.toLocalDate(), reservation.getId()));
		sql.changeTeeTimeStatus(tee.id(), "BOOKED");

		short order = 1;
		players.save(new ReservationPlayer(reservation.getId(), order, member.name(), member.phone(), member.id(),
				true));
		for (PlayerRequest p : companions)
			players.save(new ReservationPlayer(reservation.getId(), ++order, p.name().trim(), blankToNull(p.phone()),
					p.memberId(), false));

		history("CREATE", reservation, null, "ERP 예약 등록", now);
		return query.detail(reservation);
	}

	/** PENDING → CONFIRMED (결제 없이 확정, 예: 현장 후불) */
	public ReservationDetail confirm(long id) {
		Reservation r = lock(id);
		ReservationStatus before = r.getStatus();
		LocalDateTime now = now();
		r.confirm(now);
		history("CONFIRM", r, before, null, now);
		return query.detail(r);
	}

	/**
	 * 현장 결제. 금액은 예약 총액으로 고정이고 PAID(pg_provider=ERP) 결제 행을 만든다. PENDING 이면 함께 CONFIRMED 로 바꾼다.
	 */
	public ReservationDetail pay(long id, String paymentMethod) {
		Reservation r = lock(id);
		ReservationStatus before = r.getStatus();
		r.requirePayable();
		if (sql.payments(id).stream().anyMatch(PaymentRow::paid))
			throw new ConflictException("이미 결제된 예약입니다.");
		LocalDateTime now = now();
		long paymentId = sql.insertErpPayment(id, paymentMethod, r.getTotalAmount(), now);
		r.paid(now);
		history("PAYMENT", r, before, paymentMethod + " " + r.getTotalAmount().toPlainString() + "원", now);
		audit.log("PAYMENT", "reservation", id,
				Map.of("paymentId", paymentId, "method", paymentMethod, "amount", r.getTotalAmount()));
		return query.detail(r);
	}

	/**
	 * 취소. ERP 현장 결제는 같은 행을 REFUNDED 로 바꾸고(예약 앱과 같은 방식), 온라인(PG) 결제 건은 실제 환불이 필요하므로 예약 앱에서
	 * 취소하도록 막는다. 체크인한 예약은 라운드를 먼저 취소해야 한다. 티타임은 BOOKED 였으면 OPEN 으로 되돌린다.
	 */
	public ReservationDetail cancel(long id, String reason) {
		Reservation r = lock(id);
		ReservationStatus before = r.getStatus();
		r.requireCancellable();
		sql.activeRoundId(id).ifPresent(roundId -> {
			throw new ConflictException("체크인한 예약입니다. 라운드(" + roundId + ")를 먼저 취소하세요.");
		});
		List<PaymentRow> paid = sql.payments(id).stream().filter(PaymentRow::paid).toList();
		if (paid.stream().anyMatch(p -> !p.erp()))
			throw new ConflictException("온라인 결제된 예약은 환불을 위해 예약 앱에서 취소해야 합니다.");

		LocalDateTime now = now();
		BigDecimal refunded = BigDecimal.ZERO;
		for (PaymentRow p : paid) {
			if (sql.refundErpPayment(p.id(), now) > 0)
				refunded = refunded.add(p.amount());
		}
		String cancelReason = reason == null || reason.isBlank() ? DEFAULT_CANCEL_REASON : reason.trim();
		r.cancel(cancelReason, now);
		releaseTeeTime(r);

		history("CANCEL", r, before,
				refunded.signum() > 0 ? cancelReason + " (환불 " + refunded.toPlainString() + "원)" : cancelReason, now);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("before", before);
		details.put("reason", cancelReason);
		details.put("refundedAmount", refunded);
		audit.log("CANCEL", "reservation", id, details);
		return query.detail(r);
	}

	/** CONFIRMED → COMPLETED (플레이 시작 시각 이후) */
	public ReservationDetail complete(long id) {
		Reservation r = lock(id);
		ReservationStatus before = r.getStatus();
		LocalDateTime now = now();
		r.complete(effectivePlayAt(r), now);
		history("COMPLETE", r, before, null, now);
		return query.detail(r);
	}

	/** CONFIRMED → NO_SHOW (플레이 시작 시각 이후, 체크인하지 않은 예약만). 티타임은 지난 시각이라 그대로 둔다. */
	public ReservationDetail noShow(long id) {
		Reservation r = lock(id);
		ReservationStatus before = r.getStatus();
		sql.activeRoundId(id).ifPresent(roundId -> {
			throw new ConflictException("체크인한 예약은 노쇼로 처리할 수 없습니다. (라운드 " + roundId + ")");
		});
		LocalDateTime now = now();
		r.noShow(effectivePlayAt(r), now);
		history("NO_SHOW", r, before, null, now);
		audit.log("NO_SHOW", "reservation", id, Map.of("before", before));
		return query.detail(r);
	}

	public ReservationDetail changeMemo(long id, String memo) {
		Reservation r = lock(id);
		LocalDateTime now = now();
		r.changeMemo(blankToNull(memo), now);
		history("MEMO", r, r.getStatus(), null, now);
		return query.detail(r);
	}

	/** 취소된 예약의 티타임이 BOOKED 이고 다른 활성 예약이 없으면 OPEN 으로 되돌린다(BLOCKED·CLOSED 는 그대로). */
	private void releaseTeeTime(Reservation r) {
		if (r.getTeeTimeId() == null)
			return;
		sql.lockTeeTime(r.getTeeTimeId()).ifPresent(t -> {
			if ("BOOKED".equals(t.status())
					&& !repo.existsByTeeTimeIdAndStatusIn(t.id(), ReservationStatus.ACTIVE))
				sql.changeTeeTimeStatus(t.id(), "OPEN");
		});
	}

	public void history(String action, Reservation r, ReservationStatus before, String reason, LocalDateTime now) {
		String staff = CurrentUser.usernameOrNull();
		String text = staff == null ? reason : "[" + staff + "] " + (reason == null ? "" : reason);
		histories.save(ReservationHistory.of(action, r, before, text == null || text.isBlank() ? null : text.trim(),
				now));
	}

	public Reservation lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("예약", id));
	}

	private LocalDateTime effectivePlayAt(Reservation r) {
		if (r.getPlayAt() != null || r.getTeeTimeId() == null)
			return r.getPlayAt();
		return sql.teeTime(r.getTeeTimeId()).map(TeeTimeRow::playAt).orElse(null);
	}

	private BigDecimal greenFeePerPlayer(TeeTimeRow tee, Long courseId, LocalDateTime playAt) {
		if (tee.greenFee() != null)
			return tee.greenFee();
		GreenFeeQuoter quoter = feeQuoter.getIfAvailable();
		if (courseId == null || quoter == null)
			throw new ConflictException("그린피가 정해지지 않은 티타임입니다. 티타임 그린피나 요금 정책을 등록하세요.");
		return quoter.perPlayerFee(courseId, playAt).orElseThrow(
				() -> new ConflictException("그린피가 정해지지 않은 티타임입니다. 티타임 그린피나 요금 정책을 등록하세요."));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
