package com.golf.erp.rental;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.rental.RentalDtos.IssueItemRequest;
import com.golf.erp.rental.RentalDtos.MarkOverdueResult;
import com.golf.erp.rental.RentalDtos.RentalBalance;
import com.golf.erp.rental.RentalDtos.RentalCreateRequest;
import com.golf.erp.rental.RentalDtos.RentalDetailResponse;
import com.golf.erp.rental.RentalDtos.RentalExtendRequest;
import com.golf.erp.rental.RentalDtos.RentalHistoryResponse;
import com.golf.erp.rental.RentalDtos.RentalIssueRequest;
import com.golf.erp.rental.RentalDtos.RentalItemFeeRequest;
import com.golf.erp.rental.RentalDtos.RentalItemRequest;
import com.golf.erp.rental.RentalDtos.RentalItemResponse;
import com.golf.erp.rental.RentalDtos.RentalPaymentRequest;
import com.golf.erp.rental.RentalDtos.RentalPaymentResponse;
import com.golf.erp.rental.RentalDtos.RentalReturnRequest;
import com.golf.erp.rental.RentalDtos.RentalSummaryResponse;
import com.golf.erp.rental.RentalDtos.ReturnItemRequest;
import com.golf.erp.rental.RentalLookup.Contact;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 장비 렌탈 접수·출고·반납·연장·취소·결제.
 * <p>
 * 재고 이동: 출고 가용→대여 중(RENT_OUT), 정상 반납 대여 중→가용(RETURN), 파손 반납 대여 중→정비 중(RETURN),
 * 분실 대여 중→총 수량에서 제외(LOSS). 접수(RESERVED)는 재고를 잡지 않으며 출고 시점에 가용 수량을 확인한다.
 * <p>
 * 동시성: 렌탈 행을 먼저 잠그고(findByIdForUpdate), 재고 행은 rental_equipment_id 오름차순으로 잠근다.
 */
@Service
@Transactional
public class RentalService {
	private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

	private final EquipmentRentalRepository repo;
	private final EquipmentRentalPaymentRepository paymentRepo;
	private final EquipmentRentalHistoryRepository historyRepo;
	private final RentalEquipmentRepository equipmentRepo;
	private final RentalStockService stocks;
	private final RentalLookup lookup;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public RentalService(EquipmentRentalRepository repo, EquipmentRentalPaymentRepository paymentRepo,
			EquipmentRentalHistoryRepository historyRepo, RentalEquipmentRepository equipmentRepo,
			RentalStockService stocks, RentalLookup lookup, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.paymentRepo = paymentRepo;
		this.historyRepo = historyRepo;
		this.equipmentRepo = equipmentRepo;
		this.stocks = stocks;
		this.lookup = lookup;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ 조회

	/**
	 * @param from,to  접수일(created_at) 범위
	 * @param keyword  대여자 이름·전화번호·렌탈 번호
	 * @param overdue  true: 대여 중이면서 반납 기한이 지난 것, false: 그 외
	 */
	@Transactional(readOnly = true)
	public PageResult<RentalSummaryResponse> list(Long golfCourseId, RentalStatus status, Long memberId,
			LocalDate from, LocalDate to, String keyword, Boolean overdue, int page, int size) {
		LocalDateTime now = now();
		Specification<EquipmentRental> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("status", status), Specs.eq("memberId", memberId),
				Specs.gte("createdAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("createdAt", to == null ? null : to.plusDays(1).atStartOfDay()),
				Specs.like(keyword, "renterName", "renterPhone", "rentalNo"));
		if (overdue != null) {
			Specification<EquipmentRental> pastDue = (root, q, cb) -> cb.and(root.get("status").in(RentalStatus.OUT),
					cb.lessThan(root.<LocalDateTime>get("dueAt"), now));
			spec = spec.and(overdue ? pastDue : Specification.not(pastDue));
		}
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Order.desc("createdAt"),
				Sort.Order.desc("id")))), r -> RentalSummaryResponse.of(r, now));
	}

	@Transactional(readOnly = true)
	public RentalDetailResponse get(long id) {
		return detail(repo.findById(id).orElseThrow(() -> NotFoundException.of("렌탈", id)));
	}

	// ------------------------------------------------------------------ 접수

	/** 렌탈 접수(RESERVED). 요금·보증금은 지정하지 않으면 품목 마스터 값을 복사한다. */
	public RentalDetailResponse create(RentalCreateRequest r) {
		long golfCourseId = r.golfCourseId();
		refs.require(Ref.GOLF_COURSE, golfCourseId);
		refs.requireIfPresent(Ref.MEMBER, r.memberId());
		if (r.reservationId() != null) {
			refs.require(Ref.RESERVATION, r.reservationId());
			lookup.reservationGolfCourseId(r.reservationId()).filter(c -> c != golfCourseId).ifPresent(c -> {
				throw new IllegalArgumentException("다른 골프장의 예약입니다. (예약 " + r.reservationId() + ")");
			});
		}
		if (r.roundId() != null) {
			refs.require(Ref.ROUND, r.roundId());
			lookup.roundGolfCourseId(r.roundId()).filter(c -> c != golfCourseId).ifPresent(c -> {
				throw new IllegalArgumentException("다른 골프장의 라운드입니다. (라운드 " + r.roundId() + ")");
			});
		}
		String renterName = blankToNull(r.renterName());
		String renterPhone = blankToNull(r.renterPhone());
		if (r.memberId() != null && (renterName == null || renterPhone == null)) {
			Contact c = lookup.member(r.memberId()).orElseThrow(() -> NotFoundException.of("회원", r.memberId()));
			if (renterName == null)
				renterName = c.name();
			if (renterPhone == null)
				renterPhone = c.phone();
		}
		if (renterName == null)
			throw new IllegalArgumentException("대여자 이름을 입력하세요(회원이면 memberId 로 대신할 수 있습니다).");

		LocalDateTime now = now();
		EquipmentRental rental = new EquipmentRental(golfCourseId, r.reservationId(), r.roundId(), r.memberId(),
				renterName, renterPhone, blankToNull(r.memo()), DocumentNo.temporary(), now);
		for (RentalItemRequest i : r.items()) {
			RentalEquipment e = findEquipment(i.rentalEquipmentId());
			if (!e.isAvailableForRent())
				throw new ConflictException("현재 대여할 수 없는 장비입니다: " + e.label());
			refs.requireIfPresent(Ref.EQUIPMENT, i.equipmentId());
			rental.addItem(e.getId(), i.equipmentId(), i.quantity(),
					i.dailyRentalFee() != null ? i.dailyRentalFee() : e.getDailyRentalFee(),
					i.rentalDays() != null ? i.rentalDays() : 1,
					i.depositAmount() != null ? i.depositAmount() : e.getDepositAmount(),
					blankToNull(i.conditionOut()));
		}
		repo.saveAndFlush(rental);
		rental.assignNo(DocumentNo.of("RT", now.toLocalDate(), rental.getId()));
		for (EquipmentRentalItem item : rental.getItems())
			history(rental, item, RentalAction.RESERVED, item.getQuantity(), null, now);
		return detail(rental);
	}

	// ------------------------------------------------------------------ 출고

	/**
	 * 출고(RESERVED → RENTED). 품목마다 가용 → 대여 중으로 재고를 옮긴다(모자라면 409, 전체 롤백).
	 * dueAt 생략 시 오늘 + (최대 대여 일수 - 1)일 23:59:59.
	 */
	public RentalDetailResponse issue(long id, RentalIssueRequest r) {
		EquipmentRental rental = lock(id);
		LocalDateTime now = now();
		LocalDateTime dueAt = r == null ? null : r.dueAt();
		if (dueAt == null) {
			int maxDays = rental.getItems().stream().mapToInt(EquipmentRentalItem::getRentalDays).max().orElse(1);
			dueAt = now.toLocalDate().plusDays(maxDays - 1L).atTime(END_OF_DAY);
			if (dueAt.isBefore(now))
				dueAt = now;
		} else if (!dueAt.isAfter(now)) {
			throw new IllegalArgumentException("반납 기한은 현재 시각 이후여야 합니다.");
		}
		Map<Long, IssueItemRequest> perItem = new HashMap<>();
		if (r != null && r.items() != null)
			for (IssueItemRequest i : r.items()) {
				rental.item(i.itemId());
				if (perItem.put(i.itemId(), i) != null)
					throw new IllegalArgumentException("같은 품목이 두 번 들어 있습니다: " + i.itemId());
				refs.requireIfPresent(Ref.EQUIPMENT, i.equipmentId());
			}

		rental.issue(now, dueAt, CurrentUser.idOrNull());
		Map<Long, RentalEquipment> equipment = equipmentOf(rental.getItems());
		for (EquipmentRentalItem item : sortedByEquipment(rental.getItems())) {
			RentalEquipment e = equipment.get(item.getRentalEquipmentId());
			if (!e.isAvailableForRent())
				throw new ConflictException("현재 대여할 수 없는 장비가 있습니다: " + e.label());
			IssueItemRequest req = perItem.get(item.getId());
			item.issue(req == null ? null : req.equipmentId(), req == null ? null : blankToNull(req.conditionOut()));
			stocks.move(e, rental.getGolfCourseId(), StockMovement.RENT_OUT, item.getQuantity(),
					RentalEquipmentStockTxn.REF_RENTAL, rental.getId(), "렌탈 출고 " + rental.getRentalNo());
			history(rental, item, RentalAction.ISSUED, item.getQuantity(), item.getConditionOut(), now);
		}
		return detail(rental);
	}

	// ------------------------------------------------------------------ 반납

	/**
	 * 반납(전체·부분). 품목별로 정상(→가용), 파손(→정비 중), 분실(→총 수량에서 제외) 수량을 받는다.
	 * 모든 품목이 처리되면 RETURNED, 남은 게 있으면 PARTIALLY_RETURNED(기한 경과 시 OVERDUE).
	 */
	public RentalDetailResponse returnItems(long id, RentalReturnRequest r) {
		EquipmentRental rental = lock(id);
		if (!rental.getStatus().isOut())
			throw new ConflictException("대여 중인 렌탈만 반납할 수 있습니다. (현재 상태: " + rental.getStatus() + ")");
		LocalDateTime now = now();
		Set<Long> seen = new HashSet<>();
		List<ReturnItemRequest> requests = new ArrayList<>(r.items());
		for (ReturnItemRequest i : requests) {
			rental.item(i.itemId());
			if (!seen.add(i.itemId()))
				throw new IllegalArgumentException("같은 품목이 두 번 들어 있습니다: " + i.itemId());
		}
		requests.sort(Comparator.comparing((ReturnItemRequest i) -> rental.item(i.itemId()).getRentalEquipmentId())
				.thenComparing(ReturnItemRequest::itemId));

		Map<Long, int[]> outcomes = damagedAndLost(historyRepo.findByEquipmentRentalIdOrderByOccurredAtAscIdAsc(id));
		Map<Long, RentalEquipment> equipment = equipmentOf(rental.getItems());
		String ref = "렌탈 반납 " + rental.getRentalNo();
		boolean anyLost = false;
		for (ReturnItemRequest req : requests) {
			EquipmentRentalItem item = rental.item(req.itemId());
			RentalEquipment e = equipment.get(item.getRentalEquipmentId());
			int good = nz(req.returnedQuantity());
			int damaged = nz(req.damagedQuantity());
			int lost = nz(req.lostQuantity());
			String conditionIn = blankToNull(req.conditionIn());
			BigDecimal fee = req.damageFee() == null ? BigDecimal.ZERO : req.damageFee();
			int[] before = outcomes.getOrDefault(item.getId(), new int[2]);
			item.settle(good, damaged, lost, conditionIn, fee, before[0] > 0, before[1] > 0, now);

			if (good > 0) {
				stocks.move(e, rental.getGolfCourseId(), StockMovement.RETURN, good,
						RentalEquipmentStockTxn.REF_RENTAL, rental.getId(), ref);
				history(rental, item, RentalAction.RETURNED, good, conditionIn, now);
			}
			if (damaged > 0) {
				stocks.move(e, rental.getGolfCourseId(), StockMovement.RETURN_DAMAGED, damaged,
						RentalEquipmentStockTxn.REF_RENTAL, rental.getId(), ref + " (파손 → 정비 중)");
				history(rental, item, RentalAction.DAMAGED, damaged, note(conditionIn, lost > 0 ? null : fee), now);
			}
			if (lost > 0) {
				anyLost = true;
				stocks.move(e, rental.getGolfCourseId(), StockMovement.LOSS_FROM_RENTED, lost,
						RentalEquipmentStockTxn.REF_RENTAL, rental.getId(), ref + " (분실)");
				history(rental, item, RentalAction.LOST, lost, note(conditionIn, fee), now);
			}
			if (damaged == 0 && lost == 0 && fee.signum() > 0)
				history(rental, item, RentalAction.FEE_CHANGED, null, note(conditionIn, fee), now);
		}
		rental.recomputeTotals();
		rental.afterReturn(CurrentUser.idOrNull(), now);
		if (anyLost)
			audit.log("RENTAL_LOSS", "equipment_rental", id, Map.of("rentalNo", rental.getRentalNo()));
		return detail(rental);
	}

	// ------------------------------------------------------------------ 연장·요금·취소·연체

	/** 반납 기한 연장. addRentalDays 를 주면 미반납 품목의 대여 일수를 늘려 대여료를 다시 계산한다. */
	public RentalDetailResponse extend(long id, RentalExtendRequest r) {
		EquipmentRental rental = lock(id);
		LocalDateTime now = now();
		LocalDateTime before = rental.getDueAt();
		rental.extend(r.dueAt(), now);
		String reason = blankToNull(r.reason());
		history(rental, null, RentalAction.EXTENDED, null,
				join("반납 기한 " + before + " → " + r.dueAt(), reason), now);
		int days = nz(r.addRentalDays());
		if (days > 0) {
			for (EquipmentRentalItem item : rental.getItems()) {
				if (item.outstandingQuantity() == 0)
					continue;
				int oldDays = item.getRentalDays();
				item.addRentalDays(days);
				history(rental, item, RentalAction.FEE_CHANGED, null,
						join("기한 연장으로 대여 일수 " + oldDays + " → " + item.getRentalDays(), reason), now);
			}
			rental.recomputeTotals();
		}
		return detail(rental);
	}

	/** 품목 요금 정정(일 대여료·대여 일수·파손료). 취소된 렌탈은 불가 */
	public RentalDetailResponse changeItemFees(long id, long itemId, RentalItemFeeRequest r) {
		if (r.dailyRentalFee() == null && r.rentalDays() == null && r.damageFee() == null)
			throw new IllegalArgumentException("바꿀 금액(dailyRentalFee, rentalDays, damageFee)을 하나 이상 입력하세요.");
		EquipmentRental rental = lock(id);
		if (rental.getStatus() == RentalStatus.CANCELLED)
			throw new ConflictException("취소된 렌탈의 요금은 바꿀 수 없습니다.");
		EquipmentRentalItem item = rental.item(itemId);
		String before = feeText(item);
		item.changeFees(r.dailyRentalFee(), r.rentalDays(), r.damageFee());
		rental.recomputeTotals();
		history(rental, item, RentalAction.FEE_CHANGED, null,
				join(before + " → " + feeText(item), blankToNull(r.reason())), now());
		return detail(rental);
	}

	/** 취소: 출고 전(RESERVED)만. 재고는 움직이지 않는다. 받은 돈은 REFUND 결제로 돌려준다. */
	public RentalDetailResponse cancel(long id, String reason) {
		EquipmentRental rental = lock(id);
		rental.cancel();
		String why = blankToNull(reason);
		history(rental, null, RentalAction.CANCELLED, null, why, now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("rentalNo", rental.getRentalNo());
		details.put("reason", why);
		audit.log("CANCEL", "equipment_rental", id, details);
		return detail(rental);
	}

	/**
	 * 기한이 지난 RENTED·PARTIALLY_RETURNED 를 OVERDUE 로 바꾼다(배치·수동 실행). golfCourseId 가 없으면 전체.
	 * DDL 이력 유형에 연체가 없어 이력 대신 감사 로그를 남긴다.
	 */
	public MarkOverdueResult markOverdue(Long golfCourseId) {
		LocalDateTime now = now();
		List<RentalStatus> statuses = List.of(RentalStatus.RENTED, RentalStatus.PARTIALLY_RETURNED);
		List<EquipmentRental> targets = golfCourseId == null ? repo.findPastDueForUpdate(statuses, now)
				: repo.findPastDueForUpdate(statuses, now, golfCourseId);
		List<Long> ids = new ArrayList<>();
		for (EquipmentRental r : targets)
			if (r.markOverdueIfPastDue(now))
				ids.add(r.getId());
		if (!ids.isEmpty()) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("golfCourseId", golfCourseId);
			details.put("rentalIds", ids);
			audit.log("MARK_OVERDUE", "equipment_rental", null, details);
		}
		return new MarkOverdueResult(ids.size(), ids);
	}

	// ------------------------------------------------------------------ 결제

	/**
	 * 수납·환불. 한도: RENTAL_FEE ≤ 미수 대여료, DAMAGE_FEE ≤ 미수 파손료, DEPOSIT ≤ 받을 보증금 잔액,
	 * REFUND ≤ 받아 둔 순액(netReceived). 취소된 렌탈은 REFUND 만 가능.
	 */
	public RentalDetailResponse addPayment(long id, RentalPaymentRequest r) {
		EquipmentRental rental = lock(id);
		if (rental.getStatus() == RentalStatus.CANCELLED && r.type() != RentalPaymentType.REFUND)
			throw new ConflictException("취소된 렌탈은 환불(REFUND)만 기록할 수 있습니다.");
		RentalBalance b = RentalBalance.of(rental, paymentRepo.findByEquipmentRentalIdOrderByPaidAtAscIdAsc(id));
		BigDecimal limit = switch (r.type()) {
		case RENTAL_FEE -> b.rentalFeeTotal().subtract(b.rentalFeePaid());
		case DAMAGE_FEE -> b.damageFeeTotal().subtract(b.damageFeePaid());
		case DEPOSIT -> b.depositTotal().subtract(b.depositPaid());
		case REFUND -> b.netReceived();
		};
		if (r.amount().compareTo(limit) > 0)
			throw new ConflictException(paymentLabel(r.type()) + " 가능 금액(" + limit.max(BigDecimal.ZERO).toPlainString()
					+ "원)을 넘습니다. (요청 " + r.amount().toPlainString() + "원)");
		EquipmentRentalPayment p = paymentRepo.save(new EquipmentRentalPayment(id, r.type(), r.method(), r.amount(),
				blankToNull(r.approvalNo()), CurrentUser.idOrNull(), now()));
		if (r.type() == RentalPaymentType.REFUND)
			audit.log("REFUND", "equipment_rental_payment", p.getId(),
					Map.of("rentalId", id, "amount", p.getAmount(), "method", p.getMethod()));
		return detail(rental);
	}

	/** 결제 취소. 환불이 남아 있어 받은 돈보다 환불이 많아지는 취소는 막는다(환불부터 취소). */
	public RentalDetailResponse cancelPayment(long id, long paymentId, String reason) {
		EquipmentRental rental = lock(id);
		EquipmentRentalPayment p = paymentRepo.findByIdAndEquipmentRentalId(paymentId, id)
				.orElseThrow(() -> NotFoundException.of("렌탈 결제", paymentId));
		if (!p.isPaid())
			throw new ConflictException("이미 취소된 결제입니다.");
		if (p.getType() != RentalPaymentType.REFUND) {
			RentalBalance b = RentalBalance.of(rental, paymentRepo.findByEquipmentRentalIdOrderByPaidAtAscIdAsc(id));
			if (b.netReceived().subtract(p.getAmount()).signum() < 0)
				throw new ConflictException("이 결제를 취소하면 환불액이 받은 금액보다 많아집니다. 환불 내역을 먼저 취소하세요.");
		}
		p.cancel(now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("rentalId", id);
		details.put("type", p.getType());
		details.put("amount", p.getAmount());
		details.put("reason", blankToNull(reason));
		audit.log("CANCEL", "equipment_rental_payment", paymentId, details);
		return detail(rental);
	}

	// ------------------------------------------------------------------ 내부

	private RentalDetailResponse detail(EquipmentRental r) {
		LocalDateTime now = now();
		List<EquipmentRentalHistory> history = r.getId() == null ? List.of()
				: historyRepo.findByEquipmentRentalIdOrderByOccurredAtAscIdAsc(r.getId());
		List<EquipmentRentalPayment> payments = paymentRepo.findByEquipmentRentalIdOrderByPaidAtAscIdAsc(r.getId());
		Map<Long, int[]> outcomes = damagedAndLost(history);
		Map<Long, RentalEquipment> equipment = equipmentOf(r.getItems());
		List<RentalItemResponse> items = r.getItems().stream().map(i -> {
			int[] o = outcomes.getOrDefault(i.getId(), new int[2]);
			return RentalItemResponse.of(i, equipment.get(i.getRentalEquipmentId()), o[0], o[1]);
		}).toList();
		return new RentalDetailResponse(r.getId(), r.getRentalNo(), r.getGolfCourseId(), r.getReservationId(),
				r.getRoundId(), r.getMemberId(), r.getRenterName(), r.getRenterPhone(), r.getStatus(), r.getRentedAt(),
				r.getDueAt(), r.getReturnedAt(), r.getRentalFeeTotal(), r.getDepositTotal(), r.getDamageFeeTotal(),
				r.isOverdue(now), r.getReceivedBy(), r.getReturnedTo(), r.getMemo(), r.getCreatedAt(), items,
				payments.stream().map(RentalPaymentResponse::of).toList(),
				history.stream().map(RentalHistoryResponse::of).toList(), RentalBalance.of(r, payments));
	}

	/** 품목 id → [파손 수량, 분실 수량] (이력 집계) */
	private static Map<Long, int[]> damagedAndLost(List<EquipmentRentalHistory> history) {
		Map<Long, int[]> m = new HashMap<>();
		for (EquipmentRentalHistory h : history) {
			if (h.getEquipmentRentalItemId() == null || h.getQuantity() == null)
				continue;
			if (h.getAction() == RentalAction.DAMAGED)
				m.computeIfAbsent(h.getEquipmentRentalItemId(), k -> new int[2])[0] += h.getQuantity();
			else if (h.getAction() == RentalAction.LOST)
				m.computeIfAbsent(h.getEquipmentRentalItemId(), k -> new int[2])[1] += h.getQuantity();
		}
		return m;
	}

	private Map<Long, RentalEquipment> equipmentOf(List<EquipmentRentalItem> items) {
		Map<Long, RentalEquipment> m = new HashMap<>();
		equipmentRepo.findAllById(items.stream().map(EquipmentRentalItem::getRentalEquipmentId).distinct().toList())
				.forEach(e -> m.put(e.getId(), e));
		return m;
	}

	/** 재고 행 잠금 순서를 맞추려고 rental_equipment_id 오름차순으로 정렬 */
	private static List<EquipmentRentalItem> sortedByEquipment(List<EquipmentRentalItem> items) {
		return items.stream().sorted(Comparator.comparing(EquipmentRentalItem::getRentalEquipmentId)
				.thenComparing(EquipmentRentalItem::getId)).toList();
	}

	private void history(EquipmentRental rental, EquipmentRentalItem item, RentalAction action, Integer quantity,
			String note, LocalDateTime now) {
		historyRepo.save(new EquipmentRentalHistory(rental.getId(), item == null ? null : item.getId(), action,
				quantity, note, CurrentUser.idOrNull(), now));
	}

	private EquipmentRental lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("렌탈", id));
	}

	private RentalEquipment findEquipment(long id) {
		return equipmentRepo.findById(id).orElseThrow(() -> NotFoundException.of("렌탈 장비", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String feeText(EquipmentRentalItem i) {
		return "일 대여료 " + i.getDailyRentalFee().toPlainString() + "원 × " + i.getRentalDays() + "일, 파손료 "
				+ i.getDamageFee().toPlainString() + "원";
	}

	private static String note(String condition, BigDecimal fee) {
		String feeText = fee != null && fee.signum() > 0 ? "변상금 +" + fee.toPlainString() + "원" : null;
		return join(condition, feeText);
	}

	private static String join(String a, String b) {
		if (a == null)
			return b;
		return b == null ? a : a + " / " + b;
	}

	private static String paymentLabel(RentalPaymentType t) {
		return switch (t) {
		case RENTAL_FEE -> "대여료";
		case DAMAGE_FEE -> "파손료";
		case DEPOSIT -> "보증금";
		case REFUND -> "환불";
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String blankToNull(String s) {
		return RentalEquipmentService.blankToNull(s);
	}
}
