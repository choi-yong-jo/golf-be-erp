package com.golf.erp.round;

import com.golf.erp.billing.BillingService;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.port.ReservationLifecycle;
import com.golf.erp.common.port.ReservationLifecycle.CheckInTarget;
import com.golf.erp.round.RoundDtos.CheckInRequest;
import com.golf.erp.round.RoundDtos.PlayerRequest;
import com.golf.erp.round.RoundDtos.PlayerResponse;
import com.golf.erp.round.RoundDtos.PlayerUpdateRequest;
import com.golf.erp.round.RoundDtos.RoundDetail;
import com.golf.erp.round.RoundLookup.CourseInfo;
import com.golf.erp.round.RoundLookup.MemberInfo;
import com.golf.erp.round.RoundLookup.TeeTimeInfo;
import com.golf.erp.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 라운드 진행: 체크인 → 출발(첫 코스) → 코스 이동 → 종료, 취소, 참가자 관리.
 * 라운드를 바꾸는 요청은 모두 라운드 행을 잠가(PESSIMISTIC_WRITE) 직렬화하고, 라운드에는 @Version 이 있어
 * 잠금 없이 바꾸는 다른 경로와의 충돌도 409 로 드러난다.
 * 동시에 같은 회원·예약을 두 라운드에 넣는 경합은 부분 유니크 인덱스가 최종 방어선이다.
 */
@Service
@Transactional
public class RoundService {
	static final int MAX_PLAYERS = 8;
	static final int MAX_COURSE_SEGMENTS = 4;
	private static final Set<Short> HOLE_OPTIONS = Set.of((short) 9, (short) 18, (short) 27, (short) 36);
	private static final short DEFAULT_HOLES = 18;

	private final GolfRoundRepository rounds;
	private final RoundPlayerRepository players;
	private final RoundCourseRepository roundCourses;
	private final ScoreRepository scores;
	private final RoundEventRepository eventRepo;
	private final AssignmentService assignments;
	private final BillingService billing;
	private final ReservationLifecycle reservations;
	private final RoundLookup lookup;
	private final RoundEventRecorder events;
	private final RoundQueryService queries;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public RoundService(GolfRoundRepository rounds, RoundPlayerRepository players, RoundCourseRepository roundCourses,
			ScoreRepository scores, RoundEventRepository eventRepo, AssignmentService assignments,
			BillingService billing, ReservationLifecycle reservations, RoundLookup lookup, RoundEventRecorder events,
			RoundQueryService queries, RefChecker refs, AuditLogger audit, Clock clock) {
		this.rounds = rounds;
		this.players = players;
		this.roundCourses = roundCourses;
		this.scores = scores;
		this.eventRepo = eventRepo;
		this.assignments = assignments;
		this.billing = billing;
		this.reservations = reservations;
		this.lookup = lookup;
		this.events = events;
		this.queries = queries;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	// ── 체크인 ──

	/**
	 * 워크인(players 필수) 또는 예약(reservationId)으로 체크인한다.
	 * 참가자마다 그린피를 부과하고(예약 그린피 → 티타임 그린피 → 그린피 정책, 없으면 부과하지 않음),
	 * 예약 단계에서 결제된 금액이 있으면 선결제(PREPAID) 수납으로 옮겨 적는다.
	 */
	public RoundDetail checkIn(CheckInRequest r) {
		CheckInTarget target = r.reservationId() == null ? null : reservations.checkInTarget(r.reservationId());
		if (target != null)
			rounds.findFirstByReservationIdAndStatusNot(target.reservationId(), RoundStatus.CANCELLED).ifPresent(x -> {
				throw new ConflictException("이미 체크인한 예약입니다(라운드 " + x.getRoundNo() + ").");
			});
		Long teeTimeId = target != null ? target.teeTimeId() : r.teeTimeId();
		TeeTimeInfo tee = teeTimeId == null ? null
				: lookup.teeTime(teeTimeId).orElseThrow(() -> NotFoundException.of("티타임", teeTimeId));

		Long golfCourseId = first(target == null ? null : target.golfCourseId(), tee == null ? null : tee.golfCourseId(),
				r.golfCourseId());
		if (golfCourseId == null)
			throw new IllegalArgumentException("골프장(golfCourseId)을 지정하세요.");
		if (r.golfCourseId() != null && !r.golfCourseId().equals(golfCourseId))
			throw new IllegalArgumentException("예약·티타임의 골프장과 golfCourseId 가 다릅니다.");
		refs.require(Ref.GOLF_COURSE, golfCourseId);
		LocalDate playDate = first(target == null || target.playAt() == null ? null : target.playAt().toLocalDate(),
				tee == null ? null : tee.playDate(), r.playDate(), LocalDate.now(clock));
		LocalTime teeOff = first(r.teeOffTime(), tee == null ? null : tee.startTime(),
				target == null || target.playAt() == null ? null : target.playAt().toLocalTime());
		short holes = first(r.plannedHoles(), tee == null ? null : tee.holes(), DEFAULT_HOLES);
		if (!HOLE_OPTIONS.contains(holes))
			throw new IllegalArgumentException("홀 수는 9·18·27·36 중 하나여야 합니다.");
		billing.assertOpen(golfCourseId, playDate);

		List<PlayerRequest> requests = r.players() != null && !r.players().isEmpty() ? r.players()
				: target != null ? fromReservation(target) : List.of();
		validatePlayers(requests);

		LocalDateTime now = now();
		GolfRound round;
		try {
			round = rounds.saveAndFlush(new GolfRound(golfCourseId, target == null ? null : target.reservationId(),
					teeTimeId, playDate, teeOff, holes, blankToNull(r.memo()), CurrentUser.idOrNull(), now));
		} catch (DataIntegrityViolationException e) {
			throw new ConflictException("이미 체크인한 예약입니다. 다시 조회한 뒤 확인하세요.");
		}
		round.assignRoundNo(DocumentNo.of("RD", playDate, round.getId()));
		List<RoundPlayer> created = new ArrayList<>();
		short order = 1;
		for (PlayerRequest p : requests)
			created.add(createPlayer(round, p, order++, now));
		flushPlayers();
		events.record(round.getId(), RoundEventType.CHECK_IN, "체크인 " + created.size() + "명 ("
				+ (target == null ? "워크인" : "예약 " + target.reservationNo()) + ", " + holes + "홀)");

		billing.resolveGreenFee(round, target == null ? null : target.greenFeePerPlayer())
				.ifPresent(fee -> billing.chargeGreenFees(round, created, fee));
		if (target != null && target.paidAmount() != null && target.paidAmount().signum() > 0)
			billing.recordPrepaid(round, target.paidAmount(), "예약 " + target.reservationNo() + " 선결제");
		return queries.detail(round.getId());
	}

	// ── 참가자 ──

	public PlayerResponse addPlayer(long roundId, PlayerRequest r) {
		GolfRound round = lockActive(roundId);
		List<RoundPlayer> current = players.findByRoundIdOrderByPlayerOrder(roundId);
		if (current.size() >= MAX_PLAYERS)
			throw new ConflictException("한 팀은 최대 " + MAX_PLAYERS + "명입니다.");
		validatePlayers(List.of(r));
		Set<Short> used = current.stream().map(RoundPlayer::getPlayerOrder).collect(Collectors.toSet());
		short order = 1;
		while (used.contains(order))
			order++;
		LocalDateTime now = now();
		RoundPlayer p = createPlayer(round, r, order, now);
		flushPlayers();
		events.record(roundId, RoundEventType.PLAYER_ADDED, null, p.getId(), p.getName() + " 추가");
		billing.chargeGreenFeeForAddedPlayer(round, p);
		round.touch(now);
		return PlayerResponse.of(p);
	}

	/** 이름·전화·핸디캡·락커 번호 수정. 취소된 라운드는 바꿀 수 없다. */
	public PlayerResponse updatePlayer(long roundId, long playerId, PlayerUpdateRequest r) {
		GolfRound round = lock(roundId);
		if (round.getStatus() == RoundStatus.CANCELLED)
			throw new ConflictException("취소된 라운드입니다.");
		RoundPlayer p = findPlayer(roundId, playerId);
		String lockerNo = blankToNull(r.lockerNo());
		if (lockerNo != null && !p.isCheckedOut() && !lockerNo.equals(p.getLockerNo()))
			requireLockerFree(round, lockerNo, p.getId());
		p.update(r.name().trim(), blankToNull(r.phone()), r.handicap(), lockerNo);
		round.touch(now());
		return PlayerResponse.of(p);
	}

	/**
	 * 참가자 제외. 스코어가 있거나 수납(취소되지 않은)이 있으면 뺄 수 없고, 마지막 한 명은 뺄 수 없다(라운드 취소).
	 * 그 참가자의 요금은 취소되어 팀 공통 기록으로 남는다.
	 */
	public void removePlayer(long roundId, long playerId) {
		GolfRound round = lockActive(roundId);
		RoundPlayer p = findPlayer(roundId, playerId);
		if (players.findByRoundIdOrderByPlayerOrder(roundId).size() == 1)
			throw new ConflictException("마지막 참가자는 뺄 수 없습니다. 라운드를 취소하세요.");
		if (scores.existsByRoundPlayerId(playerId))
			throw new ConflictException(p.getName() + " 님은 스코어가 기록되어 있어 뺄 수 없습니다. 체크아웃으로 처리하세요.");
		billing.detachPlayer(round, p);
		eventRepo.detachPlayer(playerId);
		players.delete(p);
		players.flush();
		events.record(roundId, RoundEventType.PLAYER_REMOVED, p.getName() + " 제외");
		round.touch(now());
	}

	/** 개별 체크아웃(먼저 떠나는 참가자). 라운드 종료 때는 남은 참가자가 모두 체크아웃된다. */
	public PlayerResponse checkOutPlayer(long roundId, long playerId) {
		GolfRound round = lockActive(roundId);
		RoundPlayer p = findPlayer(roundId, playerId);
		LocalDateTime now = now();
		p.checkOut(now);
		round.touch(now);
		return PlayerResponse.of(p);
	}

	// ── 진행 ──

	/** 출발: 첫 코스 사용을 시작한다(round_course 1구간). */
	public RoundDetail start(long roundId, long courseId) {
		GolfRound round = lock(roundId);
		CourseInfo course = usableCourse(round, courseId);
		LocalDateTime now = now();
		round.start(courseId, now);
		roundCourses.save(new RoundCourse(roundId, courseId, (short) 1, now));
		events.record(roundId, RoundEventType.START, courseId, null, course.name() + " 코스 출발");
		return queries.detail(roundId);
	}

	/** 코스 이동(예: OUT 9홀 → IN): 지금 구간을 닫고 다음 구간을 연다. 라운드당 최대 4구간. */
	public RoundDetail changeCourse(long roundId, long courseId) {
		GolfRound round = lock(roundId);
		round.requirePlaying();
		RoundCourse open = roundCourses.findByRoundIdAndFinishedAtIsNull(roundId)
				.orElseThrow(() -> new ConflictException("사용 중인 코스 기록이 없습니다."));
		if (open.getSeqNo() >= MAX_COURSE_SEGMENTS)
			throw new ConflictException("한 라운드는 최대 " + MAX_COURSE_SEGMENTS + "개 코스 구간까지 기록합니다.");
		CourseInfo next = usableCourse(round, courseId);
		String from = lookup.courseNames(Set.of(open.getCourseId())).getOrDefault(open.getCourseId(), "-");
		LocalDateTime now = now();
		open.finish(now);
		// 열린 구간은 라운드당 하나(ux_round_course_open). Hibernate 는 INSERT 를 UPDATE 보다 먼저 보내므로 닫기를 먼저 반영한다.
		roundCourses.flush();
		roundCourses.save(new RoundCourse(roundId, courseId, (short) (open.getSeqNo() + 1), now));
		round.moveTo(courseId, now);
		events.record(roundId, RoundEventType.COURSE_CHANGED, courseId, null, from + " → " + next.name());
		return queries.detail(roundId);
	}

	/**
	 * 종료: 코스 사용을 닫고, 캐디·카트 배정을 모두 해제하고, 남은 참가자를 체크아웃한다.
	 * 예약으로 온 팀이면 예약을 COMPLETED 로 바꾼다. 요금·수납은 일 마감 전까지 계속 처리할 수 있다.
	 */
	public RoundDetail finish(long roundId) {
		GolfRound round = lock(roundId);
		LocalDateTime now = now();
		round.finish(now);
		roundCourses.findByRoundIdAndFinishedAtIsNull(roundId).ifPresent(c -> c.finish(now));
		assignments.releaseAll(round, "라운드 종료", now);
		players.findByRoundIdOrderByPlayerOrder(roundId).forEach(p -> p.checkOutIfPresent(now));
		if (round.getReservationId() != null)
			reservations.markCompleted(round.getReservationId());
		events.record(roundId, RoundEventType.FINISH, "라운드 종료");
		return queries.detail(roundId);
	}

	/**
	 * 취소(종료 전만). 수납이 남아 있으면 거부하고(먼저 수납 취소), 요금은 모두 취소한다.
	 * 배정 해제·참가자 체크아웃. 예약 상태는 바꾸지 않는다(예약은 예약 화면에서 처리).
	 */
	public RoundDetail cancel(long roundId, String reason) {
		GolfRound round = lock(roundId);
		round.requireActive();
		String why = reason.trim();
		billing.prepareRoundCancel(round, why);
		LocalDateTime now = now();
		roundCourses.findByRoundIdAndFinishedAtIsNull(roundId).ifPresent(c -> c.finish(now));
		round.cancel(why, now);
		assignments.releaseAll(round, "라운드 취소", now);
		players.findByRoundIdOrderByPlayerOrder(roundId).forEach(p -> p.checkOutIfPresent(now));
		events.record(roundId, RoundEventType.CANCEL, "취소: " + why);
		audit.log("CANCEL", "round", roundId, Map.of("roundNo", String.valueOf(round.getRoundNo()), "reason", why));
		return queries.detail(roundId);
	}

	// ── 내부 ──

	private List<PlayerRequest> fromReservation(CheckInTarget t) {
		List<PlayerRequest> list = t.players() == null ? List.of()
				: t.players().stream().sorted(Comparator.comparingInt(ReservationLifecycle.Player::order))
						.map(p -> new PlayerRequest(p.memberId(), p.name(), p.phone(), null, null)).toList();
		if (!list.isEmpty())
			return list;
		if (t.memberId() == null)
			throw new IllegalArgumentException("예약에 참가자 정보가 없습니다. 참가자(players)를 입력하세요.");
		return List.of(new PlayerRequest(t.memberId(), null, null, null, null));
	}

	private static void validatePlayers(List<PlayerRequest> requests) {
		if (requests.isEmpty())
			throw new IllegalArgumentException("참가자를 한 명 이상 입력하세요.");
		if (requests.size() > MAX_PLAYERS)
			throw new IllegalArgumentException("한 팀은 최대 " + MAX_PLAYERS + "명입니다.");
		Set<Long> members = new HashSet<>();
		Set<String> lockers = new HashSet<>();
		for (PlayerRequest p : requests) {
			if (p.memberId() == null && (p.name() == null || p.name().isBlank()))
				throw new IllegalArgumentException("비회원 참가자는 이름이 필요합니다.");
			if (p.memberId() != null && !members.add(p.memberId()))
				throw new IllegalArgumentException("같은 회원이 두 번 들어 있습니다(회원 " + p.memberId() + ").");
			String locker = blankToNull(p.lockerNo());
			if (locker != null && !lockers.add(locker))
				throw new IllegalArgumentException("같은 락커 번호가 두 번 들어 있습니다: " + locker);
		}
	}

	/** 회원이면 회원 상태·다른 라운드 참여 여부를 확인하고, 비운 이름·전화는 회원 정보로 채운다. */
	private RoundPlayer createPlayer(GolfRound round, PlayerRequest r, short order, LocalDateTime now) {
		String name = blankToNull(r.name());
		String phone = blankToNull(r.phone());
		if (r.memberId() != null) {
			MemberInfo m = lookup.member(r.memberId()).orElseThrow(() -> NotFoundException.of("회원", r.memberId()));
			if (!"ACTIVE".equals(m.status()))
				throw new ConflictException(m.name() + " 회원은 이용할 수 없는 상태입니다(" + m.status() + ").");
			players.findFirstByMemberIdAndCheckedOutAtIsNull(m.id()).ifPresent(x -> {
				String no = rounds.findById(x.getRoundId()).map(GolfRound::getRoundNo)
						.orElse(String.valueOf(x.getRoundId()));
				throw new ConflictException(m.name() + " 회원은 이미 다른 라운드(" + no + ")에 체크인되어 있습니다.");
			});
			name = name == null ? m.name() : name;
			phone = phone == null ? m.phone() : phone;
		}
		String lockerNo = blankToNull(r.lockerNo());
		if (lockerNo != null)
			requireLockerFree(round, lockerNo, -1L);
		return players.save(new RoundPlayer(round.getId(), order, r.memberId(), name, phone, r.handicap(), lockerNo,
				now));
	}

	private void requireLockerFree(GolfRound round, String lockerNo, long exceptPlayerId) {
		if (players.countLockerInUse(round.getGolfCourseId(), lockerNo, exceptPlayerId) > 0)
			throw new ConflictException("사용 중인 락커입니다: " + lockerNo);
	}

	/** 회원 중복 참여(ux_round_player_active_member)·순번 중복을 친절한 409 로 바꾼다. */
	private void flushPlayers() {
		try {
			players.flush();
		} catch (DataIntegrityViolationException e) {
			throw new ConflictException("이미 다른 라운드에 체크인된 회원이 있거나 참가자 순번이 겹칩니다. 다시 조회한 뒤 시도하세요.");
		}
	}

	private CourseInfo usableCourse(GolfRound round, long courseId) {
		CourseInfo c = lookup.course(courseId).orElseThrow(() -> NotFoundException.of("코스", courseId));
		if (c.golfCourseId() != round.getGolfCourseId())
			throw new IllegalArgumentException("다른 골프장의 코스입니다: " + c.name());
		if (!c.usable())
			throw new ConflictException(c.name() + " 코스는 지금 사용할 수 없습니다(" + c.status() + ").");
		return c;
	}

	private RoundPlayer findPlayer(long roundId, long playerId) {
		return players.findById(playerId).filter(p -> p.getRoundId().equals(roundId))
				.orElseThrow(() -> NotFoundException.of("라운드 참가자", playerId));
	}

	private GolfRound lock(long roundId) {
		return rounds.findByIdForUpdate(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
	}

	private GolfRound lockActive(long roundId) {
		GolfRound round = lock(roundId);
		round.requireActive();
		return round;
	}

	@SafeVarargs
	private static <T> T first(T... values) {
		for (T v : values)
			if (v != null)
				return v;
		return null;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
