package com.golf.erp.billing;

import com.golf.erp.billing.BillingDtos.BillLine;
import com.golf.erp.billing.BillingDtos.BillResponse;
import com.golf.erp.billing.BillingDtos.BillSummary;
import com.golf.erp.billing.BillingDtos.ChargeCreateRequest;
import com.golf.erp.billing.BillingDtos.ChargeResponse;
import com.golf.erp.billing.BillingDtos.PaymentCreateRequest;
import com.golf.erp.billing.BillingDtos.PaymentResponse;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.port.GreenFeeQuoter;
import com.golf.erp.round.GolfRound;
import com.golf.erp.round.GolfRoundRepository;
import com.golf.erp.round.RoundEventRecorder;
import com.golf.erp.round.RoundEventType;
import com.golf.erp.round.RoundLookup;
import com.golf.erp.round.RoundPlayer;
import com.golf.erp.round.RoundPlayerRepository;
import com.golf.erp.round.RoundSettlementStatus;
import com.golf.erp.round.RoundStatus;
import com.golf.erp.round.RoundLookup.TeeTimeInfo;
import com.golf.erp.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 라운드 요금·수납.
 * <ul>
 * <li>변경은 모두 라운드 행을 잠근 뒤(PESSIMISTIC_WRITE) 처리하고, 끝나면 round.settlement_status 를 다시 계산한다.</li>
 * <li>그 라운드의 골프장·영업일(play_date)이 일 마감(CLOSED)되었으면 요금·수납을 바꿀 수 없다(409).
 * 일 마감은 그날 라운드 행을 모두 잠근 뒤 계산하므로, 라운드 잠금 → 마감 여부 확인 순서로 경합이 없다.</li>
 * <li>취소된 라운드에는 요금·수납을 넣을 수 없다. 종료된 라운드는 마감 전까지 정산할 수 있다.</li>
 * </ul>
 * RoundService 가 체크인·참가자 변경·취소 때 부르는 메서드(호출자가 라운드를 이미 잠근 상태)도 여기 있다.
 */
@Service
@Transactional
public class BillingService {
	private static final String TEAM_LABEL = "팀 공통";

	private final GolfRoundRepository rounds;
	private final RoundPlayerRepository players;
	private final RoundChargeRepository charges;
	private final RoundPaymentRepository payments;
	private final ServiceFeeRepository serviceFees;
	private final DailySettlementRepository settlements;
	private final RoundLookup lookup;
	private final GreenFeeQuoter greenFeeQuoter;
	private final RoundEventRecorder events;
	private final AuditLogger audit;
	private final Clock clock;

	public BillingService(GolfRoundRepository rounds, RoundPlayerRepository players, RoundChargeRepository charges,
			RoundPaymentRepository payments, ServiceFeeRepository serviceFees, DailySettlementRepository settlements,
			RoundLookup lookup, GreenFeeQuoter greenFeeQuoter, RoundEventRecorder events, AuditLogger audit,
			Clock clock) {
		this.rounds = rounds;
		this.players = players;
		this.charges = charges;
		this.payments = payments;
		this.serviceFees = serviceFees;
		this.settlements = settlements;
		this.lookup = lookup;
		this.greenFeeQuoter = greenFeeQuoter;
		this.events = events;
		this.audit = audit;
		this.clock = clock;
	}

	// ── 조회 ──

	@Transactional(readOnly = true)
	public List<ChargeResponse> charges(long roundId) {
		findRound(roundId);
		Map<Long, String> names = playerNames(roundId);
		return charges.findByRoundIdOrderById(roundId).stream().map(c -> ChargeResponse.of(c, names)).toList();
	}

	@Transactional(readOnly = true)
	public List<PaymentResponse> payments(long roundId) {
		findRound(roundId);
		Map<Long, String> names = playerNames(roundId);
		return payments.findByRoundIdOrderById(roundId).stream().map(p -> PaymentResponse.of(p, names)).toList();
	}

	/** 계산서: 요금·수납 목록과 참가자별·팀 공통 합계 */
	@Transactional(readOnly = true)
	public BillResponse bill(long roundId) {
		GolfRound round = findRound(roundId);
		List<RoundPlayer> roundPlayers = players.findByRoundIdOrderByPlayerOrder(roundId);
		Map<Long, String> names = roundPlayers.stream()
				.collect(Collectors.toMap(RoundPlayer::getId, RoundPlayer::getName));
		List<RoundCharge> chargeList = charges.findByRoundIdOrderById(roundId);
		List<RoundPayment> paymentList = payments.findByRoundIdOrderById(roundId);

		Map<Long, BigDecimal> chargeBy = new LinkedHashMap<>();
		Map<Long, BigDecimal> paidBy = new LinkedHashMap<>();
		chargeList.stream().filter(c -> !c.isVoided())
				.forEach(c -> chargeBy.merge(key(c.getRoundPlayerId()), c.getAmount(), BigDecimal::add));
		paymentList.stream().filter(RoundPayment::isPaid)
				.forEach(p -> paidBy.merge(key(p.getRoundPlayerId()), p.getAmount(), BigDecimal::add));
		List<BillLine> lines = new ArrayList<>();
		lines.add(line(null, TEAM_LABEL, chargeBy.get(0L), paidBy.get(0L)));
		for (RoundPlayer p : roundPlayers)
			lines.add(line(p.getId(), p.getName(), chargeBy.get(p.getId()), paidBy.get(p.getId())));

		return new BillResponse(round.getId(), round.getRoundNo(), round.getStatus(), round.getSettlementStatus(),
				isClosed(round.getGolfCourseId(), round.getPlayDate()), summarize(chargeList, paymentList), lines,
				chargeList.stream().map(c -> ChargeResponse.of(c, names)).toList(),
				paymentList.stream().map(p -> PaymentResponse.of(p, names)).toList());
	}

	/** 라운드 상세 화면용 요금·수납 합계 */
	@Transactional(readOnly = true)
	public BillSummary summary(long roundId) {
		return summarize(charges.findByRoundIdOrderById(roundId), payments.findByRoundIdOrderById(roundId));
	}

	// ── 요금 ──

	public List<ChargeResponse> addCharge(long roundId, ChargeCreateRequest r) {
		GolfRound round = lockForBilling(roundId);
		RoundPlayer player = findPlayer(round, r.roundPlayerId());
		short quantity = r.quantity() == null ? 1 : r.quantity();
		LocalDateTime now = now();
		List<RoundCharge> created = new ArrayList<>();

		if (r.serviceFeeId() != null) {
			ServiceFee fee = serviceFees.findById(r.serviceFeeId())
					.orElseThrow(() -> NotFoundException.of("부대 요금", r.serviceFeeId()));
			if (!fee.getGolfCourseId().equals(round.getGolfCourseId()))
				throw new IllegalArgumentException("다른 골프장의 부대 요금입니다.");
			if (!fee.appliesOn(round.getPlayDate()))
				throw new ConflictException(fee.getName() + " 요금은 " + round.getPlayDate() + " 에 적용되지 않습니다(사용 중지 또는 적용 기간 밖).");
			if (fee.getHoleBasis() != null && !fee.getHoleBasis().equals(round.getPlannedHoles()))
				throw new IllegalArgumentException(fee.getName() + " 요금은 " + fee.getHoleBasis() + "홀 기준입니다(라운드 "
						+ round.getPlannedHoles() + "홀).");
			String description = r.description() == null || r.description().isBlank() ? fee.getName()
					: r.description().trim();
			List<RoundPlayer> targets = player != null || fee.getChargeUnit() == ChargeUnit.TEAM
					? Collections.singletonList(player)
					: players.findByRoundIdOrderByPlayerOrder(roundId);
			for (RoundPlayer target : targets)
				created.add(charges.save(new RoundCharge(roundId, target == null ? null : target.getId(),
						fee.getFeeType().toChargeType(), description, fee.getAmount(), quantity, r.discountAmount(),
						CurrentUser.idOrNull(), now)));
		} else {
			if (r.chargeType() == null || r.unitPrice() == null)
				throw new IllegalArgumentException("부대 요금(serviceFeeId)을 고르거나 요금 종류(chargeType)와 단가(unitPrice)를 입력하세요.");
			created.add(charges.save(new RoundCharge(roundId, player == null ? null : player.getId(), r.chargeType(),
					blankToNull(r.description()), r.unitPrice(), quantity, r.discountAmount(), CurrentUser.idOrNull(),
					now)));
		}
		Map<Long, String> names = playerNames(roundId);
		for (RoundCharge c : created)
			events.record(roundId, RoundEventType.CHARGE_ADDED, null, c.getRoundPlayerId(), describe(c, names));
		refreshSettlementStatus(round);
		return created.stream().map(c -> ChargeResponse.of(c, names)).toList();
	}

	public ChargeResponse voidCharge(long roundId, long chargeId, String reason) {
		GolfRound round = lockForBilling(roundId);
		RoundCharge c = charges.findById(chargeId).filter(x -> x.getRoundId().equals(roundId))
				.orElseThrow(() -> NotFoundException.of("요금", chargeId));
		String why = reason.trim();
		c.voidCharge(why, now());
		Map<Long, String> names = playerNames(roundId);
		events.record(roundId, RoundEventType.CHARGE_VOIDED, null, c.getRoundPlayerId(),
				describe(c, names) + " 취소: " + why);
		audit.log("VOID", "round_charge", chargeId, Map.of("roundId", roundId, "amount", c.getAmount(), "reason", why));
		refreshSettlementStatus(round);
		return ChargeResponse.of(c, names);
	}

	// ── 수납 ──

	public PaymentResponse addPayment(long roundId, PaymentCreateRequest r) {
		GolfRound round = lockForBilling(roundId);
		RoundPlayer player = findPlayer(round, r.roundPlayerId());
		RoundPayment p = payments.save(new RoundPayment(roundId, player == null ? null : player.getId(), r.method(),
				r.amount(), blankToNull(r.approvalNo()), blankToNull(r.memo()), CurrentUser.idOrNull(), now()));
		events.record(roundId, RoundEventType.PAYMENT_RECEIVED, null, p.getRoundPlayerId(),
				(player == null ? TEAM_LABEL : player.getName()) + " " + p.getMethod() + " " + won(p.getAmount()));
		refreshSettlementStatus(round);
		return PaymentResponse.of(p, playerNames(roundId));
	}

	public PaymentResponse cancelPayment(long roundId, long paymentId, String reason) {
		GolfRound round = lockForBilling(roundId);
		RoundPayment p = payments.findById(paymentId).filter(x -> x.getRoundId().equals(roundId))
				.orElseThrow(() -> NotFoundException.of("수납", paymentId));
		String why = reason.trim();
		p.cancel(why, now());
		events.record(roundId, RoundEventType.PAYMENT_CANCELLED, null, p.getRoundPlayerId(),
				p.getMethod() + " " + won(p.getAmount()) + " 취소: " + why);
		audit.log("CANCEL", "round_payment", paymentId,
				Map.of("roundId", roundId, "amount", p.getAmount(), "method", p.getMethod(), "reason", why));
		refreshSettlementStatus(round);
		return PaymentResponse.of(p, playerNames(roundId));
	}

	// ── RoundService 가 부르는 처리(호출자가 라운드를 잠근 상태) ──

	/** 그 골프장·영업일이 마감되었으면 409 */
	public void assertOpen(long golfCourseId, LocalDate businessDate) {
		if (isClosed(golfCourseId, businessDate))
			throw new ConflictException(businessDate + " 영업일은 일 마감되어 요금·수납을 바꿀 수 없습니다. 마감을 해제한 뒤 다시 시도하세요.");
	}

	/**
	 * 1인 그린피를 정한다: 예약에 기록된 그린피 → 티타임 그린피 → 그린피 정책(GreenFeeQuoter) 순. 없으면 empty.
	 */
	public Optional<BigDecimal> resolveGreenFee(GolfRound round, BigDecimal reservationFee) {
		if (reservationFee != null)
			return Optional.of(reservationFee);
		if (round.getTeeTimeId() != null) {
			Optional<BigDecimal> teeFee = lookup.teeTime(round.getTeeTimeId()).map(TeeTimeInfo::greenFee)
					.filter(Objects::nonNull);
			if (teeFee.isPresent())
				return teeFee;
		}
		LocalTime time = round.getTeeOffTime() != null ? round.getTeeOffTime() : round.getCheckedInAt().toLocalTime();
		return greenFeeQuoter.perPlayerFee(round.getGolfCourseId(), round.getPlayDate().atTime(time));
	}

	/** 참가자마다 그린피 1건(1인 그린피 × 1)을 부과한다. */
	public void chargeGreenFees(GolfRound round, List<RoundPlayer> targets, BigDecimal perPlayerFee) {
		if (targets.isEmpty())
			return;
		LocalDateTime now = now();
		String description = round.getPlannedHoles() + "홀 그린피";
		for (RoundPlayer p : targets) {
			RoundCharge c = charges.save(new RoundCharge(round.getId(), p.getId(), ChargeType.GREEN_FEE, description,
					perPlayerFee, (short) 1, BigDecimal.ZERO, CurrentUser.idOrNull(), now));
			events.record(round.getId(), RoundEventType.CHARGE_ADDED, null, p.getId(),
					"GREEN_FEE " + p.getName() + " " + won(c.getAmount()) + " (" + description + ")");
		}
		refreshSettlementStatus(round);
	}

	/**
	 * 체크인 뒤 추가된 참가자의 그린피. 같은 라운드에 이미 부과된(취소되지 않은) 그린피 단가를 그대로 쓰고,
	 * 없으면 티타임·그린피 정책에서 찾는다. 어디에도 없으면 부과하지 않는다(직접 추가).
	 */
	public void chargeGreenFeeForAddedPlayer(GolfRound round, RoundPlayer player) {
		Optional<BigDecimal> fee = charges.findByRoundIdOrderById(round.getId()).stream()
				.filter(c -> !c.isVoided() && c.getChargeType() == ChargeType.GREEN_FEE).map(RoundCharge::getUnitPrice)
				.findFirst().or(() -> resolveGreenFee(round, null));
		fee.ifPresent(f -> chargeGreenFees(round, List.of(player), f));
	}

	/** 예약 시스템에서 이미 결제한 금액을 선결제(PREPAID) 수납으로 옮겨 적는다. */
	public void recordPrepaid(GolfRound round, BigDecimal amount, String memo) {
		if (amount == null || amount.signum() <= 0)
			return;
		RoundPayment p = payments.save(new RoundPayment(round.getId(), null, PaymentMethod.PREPAID, amount, null,
				memo, CurrentUser.idOrNull(), now()));
		events.record(round.getId(), RoundEventType.PAYMENT_RECEIVED, "예약 선결제 " + won(p.getAmount()));
		refreshSettlementStatus(round);
	}

	/**
	 * 라운드 취소 준비: 마감 여부를 확인하고, 취소되지 않은 수납이 있으면 거부한다(먼저 수납 취소).
	 * 남은 요금은 모두 취소한다.
	 */
	public void prepareRoundCancel(GolfRound round, String reason) {
		assertOpen(round.getGolfCourseId(), round.getPlayDate());
		if (payments.sumPaid(round.getId()).signum() > 0)
			throw new ConflictException("수납 기록이 남아 있습니다. 수납(선결제 포함)을 먼저 취소한 뒤 라운드를 취소하세요.");
		LocalDateTime now = now();
		Map<Long, String> names = playerNames(round.getId());
		for (RoundCharge c : charges.findByRoundIdOrderById(round.getId())) {
			if (c.isVoided())
				continue;
			c.voidCharge("라운드 취소: " + reason, now);
			events.record(round.getId(), RoundEventType.CHARGE_VOIDED, null, c.getRoundPlayerId(),
					describe(c, names) + " 취소(라운드 취소)");
		}
		refreshSettlementStatus(round);
	}

	/**
	 * 참가자를 라운드에서 빼기 전 정리: 그 참가자의 취소되지 않은 수납이 있으면 거부한다.
	 * 남은 요금은 취소하고, 그 참가자를 가리키던 요금·수납 기록은 팀 공통으로 돌린다(참가자 행을 지울 수 있도록).
	 */
	public void detachPlayer(GolfRound round, RoundPlayer player) {
		List<RoundPayment> playerPayments = payments.findByRoundPlayerId(player.getId());
		if (playerPayments.stream().anyMatch(RoundPayment::isPaid))
			throw new ConflictException(player.getName() + " 님의 수납 기록이 있습니다. 수납을 먼저 취소하세요.");
		List<RoundCharge> playerCharges = charges.findByRoundPlayerId(player.getId());
		if (playerCharges.isEmpty() && playerPayments.isEmpty())
			return;
		assertOpen(round.getGolfCourseId(), round.getPlayDate());
		LocalDateTime now = now();
		Map<Long, String> names = playerNames(round.getId());
		for (RoundCharge c : playerCharges) {
			if (!c.isVoided()) {
				c.voidCharge("참가자 제외", now);
				events.record(round.getId(), RoundEventType.CHARGE_VOIDED, describe(c, names) + " 취소(참가자 제외)");
			}
			c.detachPlayer(player.getName());
		}
		playerPayments.forEach(p -> p.detachPlayer(player.getName()));
		refreshSettlementStatus(round);
	}

	/** 요금 합계·수납 합계로 라운드 수납 상태를 다시 계산한다. */
	public void refreshSettlementStatus(GolfRound round) {
		charges.flush();
		BigDecimal total = charges.sumActive(round.getId());
		BigDecimal paid = payments.sumPaid(round.getId());
		round.changeSettlementStatus(RoundSettlementStatus.of(total, paid), now());
	}

	// ── 내부 ──

	private GolfRound lockForBilling(long roundId) {
		GolfRound round = rounds.findByIdForUpdate(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
		if (round.getStatus() == RoundStatus.CANCELLED)
			throw new ConflictException("취소된 라운드에는 요금·수납을 처리할 수 없습니다.");
		assertOpen(round.getGolfCourseId(), round.getPlayDate());
		return round;
	}

	private boolean isClosed(long golfCourseId, LocalDate businessDate) {
		return settlements.existsByGolfCourseIdAndBusinessDateAndStatus(golfCourseId, businessDate,
				DailySettlementStatus.CLOSED);
	}

	private GolfRound findRound(long roundId) {
		return rounds.findById(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
	}

	private RoundPlayer findPlayer(GolfRound round, Long roundPlayerId) {
		if (roundPlayerId == null)
			return null;
		return players.findById(roundPlayerId).filter(p -> p.getRoundId().equals(round.getId()))
				.orElseThrow(() -> NotFoundException.of("라운드 참가자", roundPlayerId));
	}

	private Map<Long, String> playerNames(long roundId) {
		return players.findByRoundIdOrderByPlayerOrder(roundId).stream()
				.collect(Collectors.toMap(RoundPlayer::getId, RoundPlayer::getName, (a, b) -> a, LinkedHashMap::new));
	}

	private static BillSummary summarize(List<RoundCharge> chargeList, List<RoundPayment> paymentList) {
		BigDecimal charge = sum(chargeList.stream().filter(c -> !c.isVoided()).map(RoundCharge::getAmount).toList());
		BigDecimal discount = sum(
				chargeList.stream().filter(c -> !c.isVoided()).map(RoundCharge::getDiscountAmount).toList());
		BigDecimal paid = sum(paymentList.stream().filter(RoundPayment::isPaid).map(RoundPayment::getAmount).toList());
		return new BillSummary(charge, discount, paid, charge.subtract(paid));
	}

	private static BillLine line(Long playerId, String name, BigDecimal charge, BigDecimal paid) {
		BigDecimal c = charge == null ? zero() : charge;
		BigDecimal p = paid == null ? zero() : paid;
		return new BillLine(playerId, playerId == null ? null : name, c, p, c.subtract(p));
	}

	private static BigDecimal sum(List<BigDecimal> values) {
		return values.stream().reduce(zero(), BigDecimal::add);
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(2);
	}

	/** 팀 공통(round_player_id NULL) 묶음 키 = 0 */
	private static Long key(Long roundPlayerId) {
		return roundPlayerId == null ? 0L : roundPlayerId;
	}

	private static String describe(RoundCharge c, Map<Long, String> names) {
		String who = c.getRoundPlayerId() == null ? TEAM_LABEL
				: names.getOrDefault(c.getRoundPlayerId(), "참가자 " + c.getRoundPlayerId());
		return c.getChargeType() + " " + who + " " + won(c.getAmount())
				+ (c.getDescription() == null ? "" : " (" + c.getDescription() + ")");
	}

	static String won(BigDecimal amount) {
		return new DecimalFormat("#,##0.##").format(amount) + "원";
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
