package com.golf.erp.reservation;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.course.GolfCourse;
import com.golf.erp.course.GolfCourseService;
import com.golf.erp.fee.GreenFeePolicy;
import com.golf.erp.fee.GreenFeePolicyService;
import com.golf.erp.member.Member;
import com.golf.erp.member.MemberRepository;
import com.golf.erp.member.MemberStatus;
import com.golf.erp.member.ReservationRestriction;
import com.golf.erp.member.ReservationRestrictionService;
import com.golf.erp.payment.Payment;
import com.golf.erp.payment.PaymentRepository;
import com.golf.erp.payment.PaymentStatus;
import com.golf.erp.payment.PaymentType;
import com.golf.erp.redis.TeeTimeChangeNotifier;
import com.golf.erp.reservation.dto.ReservationCreateRequest;
import com.golf.erp.teetime.TeeTime;
import com.golf.erp.teetime.TeeTimeRepository;
import com.golf.erp.teetime.TeeTimeStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ERP(관리자) 예약 처리. 예약 앱(golf-reservation-api)과 같은 테이블을 쓰므로 그 규칙을 따른다.
 * <ul>
 * <li>동시성: 회원 행과 티타임 행을 SELECT ... FOR UPDATE 로 잠그고, DB 부분 유니크 인덱스
 * (ux_reservation_active_tee_time)가 최종적으로 중복 예약을 막는다.</li>
 * <li>예약하면 티타임을 BOOKED, 취소하면 OPEN 으로 되돌린다.</li>
 * <li>예약 앱이 결제/환불 중인 예약(PAYING, REFUND_PENDING)과 온라인(PG) 결제 건의 환불은 다루지 않는다.</li>
 * <li>모든 변경은 reservation_history 에 남긴다.</li>
 * </ul>
 */
@Service
@Transactional
public class ReservationCommandService {
	private static final String DEFAULT_CANCEL_REASON = "관리자 취소";

	private final ReservationRepository repo;
	private final ReservationPlayerRepository playerRepo;
	private final ReservationHistoryRepository historyRepo;
	private final TeeTimeRepository teeTimeRepo;
	private final MemberRepository memberRepo;
	private final ReservationRestrictionService restrictionService;
	private final GolfCourseService courseService;
	private final GreenFeePolicyService feeService;
	private final PaymentRepository paymentRepo;
	private final TeeTimeChangeNotifier notifier;
	private final Clock clock;

	public ReservationCommandService(ReservationRepository repo, ReservationPlayerRepository playerRepo,
			ReservationHistoryRepository historyRepo, TeeTimeRepository teeTimeRepo, MemberRepository memberRepo,
			ReservationRestrictionService restrictionService, GolfCourseService courseService,
			GreenFeePolicyService feeService, PaymentRepository paymentRepo, TeeTimeChangeNotifier notifier,
			Clock clock) {
		this.notifier = notifier;
		this.repo = repo;
		this.playerRepo = playerRepo;
		this.historyRepo = historyRepo;
		this.teeTimeRepo = teeTimeRepo;
		this.memberRepo = memberRepo;
		this.restrictionService = restrictionService;
		this.courseService = courseService;
		this.feeService = feeService;
		this.paymentRepo = paymentRepo;
		this.clock = clock;
	}

	public Reservation create(ReservationCreateRequest r) {
		LocalDateTime now = now();
		List<ReservationCreateRequest.PlayerRequest> companions = r.companions() == null ? List.of() : r.companions();
		if (companions.size() > r.playerCount() - 1)
			throw new IllegalArgumentException("동반자는 최대 " + (r.playerCount() - 1) + "명까지 입력할 수 있습니다.");

		Member member = memberRepo.findByIdForUpdate(r.memberId())
				.orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다: " + r.memberId()));
		if (member.getMemberStatus() != MemberStatus.ACTIVE)
			throw new ConflictException("활성 상태의 회원만 예약할 수 있습니다. (현재: " + member.getMemberStatus() + ")");
		List<ReservationRestriction> restrictions = restrictionService.activeAt(member.getMemberId(), now);
		if (!restrictions.isEmpty())
			throw new ConflictException("예약이 제한된 회원입니다: " + restrictions.get(0).getRestrictionType() + " - "
					+ restrictions.get(0).getReason());

		TeeTime tee = teeTimeRepo.findByIdForUpdate(r.teeTimeId())
				.orElseThrow(() -> new NotFoundException("티타임을 찾을 수 없습니다: " + r.teeTimeId()));
		if (tee.getTeeTimeStatus() != TeeTimeStatus.OPEN)
			throw new ConflictException("예약할 수 없는 티타임입니다. (상태: " + tee.getTeeTimeStatus() + ")");
		LocalDateTime playAt = tee.playAt();
		if (!playAt.isAfter(now))
			throw new ConflictException("이미 지난 티타임입니다.");
		if (r.playerCount() > tee.getCapacity())
			throw new IllegalArgumentException("인원이 티타임 정원(" + tee.getCapacity() + "명)을 넘습니다.");

		Long courseId = tee.getGolfCourseId();
		if (courseId != null) {
			GolfCourse course = courseService.get(courseId);
			if (!course.isOperating())
				throw new ConflictException("운영 중인 코스가 아닙니다.");
			if (!courseService.activeClosures(courseId, playAt, playAt.plusMinutes(1)).isEmpty())
				throw new ConflictException("휴장 기간의 티타임입니다.");
		}
		// 다른 시스템이 tee_time_status 를 바꾸지 않고 예약했을 수도 있어 예약 행도 확인한다
		if (repo.existsByTeeTimeIdAndReservationStatusIn(tee.getTeeTimeId(), ReservationStatus.ACTIVE))
			throw new ConflictException("이미 예약된 티타임입니다.");

		BigDecimal fee;
		BigDecimal discount;
		if (tee.getGreenFee() != null) {
			fee = tee.getGreenFee();
			discount = BigDecimal.ZERO;
		} else {
			GreenFeePolicy policy = courseId == null ? null
					: feeService.resolve(courseId, tee.getPlayDate(), tee.getStartTime()).orElse(null);
			if (policy == null)
				throw new ConflictException("그린피가 정해지지 않은 티타임입니다. 티타임 그린피나 요금 정책을 등록하세요.");
			fee = policy.getBaseFee();
			discount = policy.getDiscountAmount();
		}

		Reservation reservation = repo.saveAndFlush(new Reservation(member.getMemberId(), tee.getTeeTimeId(),
				courseId, playAt, r.playerCount(), fee, discount, blankToNull(r.memo()), now));
		reservation.assignReservationNo();
		tee.book();

		short order = 1;
		playerRepo.save(new ReservationPlayer(reservation.getReservationId(), order, member.getMemberName(),
				member.getPhone(), member.getMemberId(), true));
		for (ReservationCreateRequest.PlayerRequest p : companions)
			playerRepo.save(new ReservationPlayer(reservation.getReservationId(), ++order, p.playerName().trim(),
					blankToNull(p.phone()), p.memberId(), false));

		historyRepo.save(ReservationHistory.of("CREATE", reservation, null, null, null, null, now));
		notifier.teeTimeChanged("RESERVATION_CREATED", courseId, tee.getPlayDate(), tee.getTeeTimeId(),
				reservation.getReservationId(), reservation.getReservationStatus().name());
		return reservation;
	}

	/** PENDING → CONFIRMED (결제 없이 확정, 예: 현장 후불) */
	public Reservation confirm(long id) {
		Reservation r = lock(id);
		if (r.getReservationStatus() != ReservationStatus.PENDING)
			throw new ConflictException("대기(PENDING) 상태의 예약만 확정할 수 있습니다. (현재: " + r.getReservationStatus() + ")");
		LocalDateTime now = now();
		ReservationStatus before = r.getReservationStatus();
		r.confirm(now);
		history("CONFIRM", r, before, null, now);
		notifyUpdated(r);
		return r;
	}

	/** 현장 결제 등록. PENDING 이면 함께 CONFIRMED 로 바꾼다. */
	public Payment pay(long id, String paymentMethod) {
		Reservation r = lock(id);
		ReservationStatus before = r.getReservationStatus();
		if (before != ReservationStatus.PENDING && before != ReservationStatus.CONFIRMED)
			throw new ConflictException("결제할 수 없는 예약 상태입니다. (현재: " + before + ")");
		if (r.getTotalAmount() == null || r.getTotalAmount().signum() <= 0)
			throw new ConflictException("결제할 금액이 없습니다. 0원 예약은 확정만 하면 됩니다.");
		if (!paidPayments(id).isEmpty())
			throw new ConflictException("이미 결제된 예약입니다.");
		LocalDateTime now = now();
		Payment payment = paymentRepo.save(Payment.erpPaid(id, paymentMethod, r.getTotalAmount(),
				"ERP-" + UUID.randomUUID(), now));
		if (before == ReservationStatus.PENDING)
			r.confirm(now);
		history("PAYMENT", r, before, paymentMethod + " " + r.getTotalAmount().toPlainString(), now);
		notifyUpdated(r);
		return payment;
	}

	/**
	 * 취소. ERP 현장 결제는 같은 행을 REFUNDED 로 바꾸고(예약 앱과 같은 방식), 온라인(PG) 결제 건은 실제 환불이 필요하므로
	 * 예약 앱에서 취소하도록 막는다.
	 */
	public Reservation cancel(long id, String reason) {
		Reservation r = lock(id);
		ReservationStatus before = r.getReservationStatus();
		if (ReservationStatus.IN_PROGRESS_BY_APP.contains(before))
			throw new ConflictException("예약 앱에서 결제 또는 환불을 처리 중인 예약입니다. 잠시 후 다시 확인하세요.");
		if (before != ReservationStatus.PENDING && before != ReservationStatus.PENDING_PAYMENT
				&& before != ReservationStatus.CONFIRMED)
			throw new ConflictException("취소할 수 없는 예약 상태입니다. (현재: " + before + ")");

		List<Payment> paid = paidPayments(id);
		if (paid.stream().anyMatch(p -> !p.isErpPayment()))
			throw new ConflictException("온라인 결제된 예약은 환불을 위해 예약 앱에서 취소해야 합니다.");
		LocalDateTime now = now();
		paid.forEach(p -> p.refund(now));

		String cancelReason = reason == null || reason.isBlank() ? DEFAULT_CANCEL_REASON : reason.trim();
		r.cancel(cancelReason, now);
		TeeTime tee = r.getTeeTimeId() == null ? null : teeTimeRepo.findByIdForUpdate(r.getTeeTimeId()).orElse(null);
		if (tee != null)
			tee.release();
		history("CANCEL", r, before, cancelReason, now);
		notifier.teeTimeChanged("RESERVATION_CANCELLED", courseIdOf(r, tee), playDateOf(r, tee), r.getTeeTimeId(),
				r.getReservationId(), r.getReservationStatus().name());
		return r;
	}

	/** CONFIRMED → COMPLETED (플레이 완료) */
	public Reservation complete(long id) {
		return finish(id, ReservationStatus.COMPLETED, "COMPLETE");
	}

	/** CONFIRMED → NO_SHOW (미방문) */
	public Reservation noShow(long id) {
		return finish(id, ReservationStatus.NO_SHOW, "NO_SHOW");
	}

	public Reservation changeMemo(long id, String memo) {
		Reservation r = lock(id);
		LocalDateTime now = now();
		r.changeMemo(blankToNull(memo), now);
		history("MEMO", r, r.getReservationStatus(), null, now);
		return r;
	}

	private Reservation finish(long id, ReservationStatus target, String action) {
		Reservation r = lock(id);
		ReservationStatus before = r.getReservationStatus();
		if (before != ReservationStatus.CONFIRMED)
			throw new ConflictException("확정(CONFIRMED)된 예약만 처리할 수 있습니다. (현재: " + before + ")");
		LocalDateTime now = now();
		if (r.getPlayAt() != null && r.getPlayAt().isAfter(now))
			throw new ConflictException("플레이 시작 시각 이후에 처리할 수 있습니다.");
		r.finish(target, now);
		history(action, r, before, null, now);
		notifyUpdated(r);
		return r;
	}

	/** 티타임 상태는 그대로인 예약 변경 알림 (예약 현황 화면 갱신용) */
	private void notifyUpdated(Reservation r) {
		TeeTime tee = r.getTeeTimeId() == null ? null : teeTimeRepo.findById(r.getTeeTimeId()).orElse(null);
		LocalDate playDate = playDateOf(r, tee);
		if (playDate != null)
			notifier.reservationUpdated(courseIdOf(r, tee), playDate, r.getTeeTimeId(), r.getReservationId(),
					r.getReservationStatus().name());
	}

	private static Long courseIdOf(Reservation r, TeeTime tee) {
		return r.getGolfCourseId() != null ? r.getGolfCourseId() : tee == null ? null : tee.getGolfCourseId();
	}

	private static LocalDate playDateOf(Reservation r, TeeTime tee) {
		return r.getPlayAt() != null ? r.getPlayAt().toLocalDate() : tee == null ? null : tee.getPlayDate();
	}

	private Reservation lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("예약을 찾을 수 없습니다: " + id));
	}

	private List<Payment> paidPayments(long reservationId) {
		return paymentRepo.findByReservationIdAndPaymentTypeAndPaymentStatus(reservationId, PaymentType.PAYMENT,
				PaymentStatus.PAID);
	}

	private void history(String action, Reservation r, ReservationStatus before, String reason, LocalDateTime now) {
		historyRepo.save(ReservationHistory.of(action, r, before, r.getTeeTimeId(), r.getTotalAmount(), reason, now));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
