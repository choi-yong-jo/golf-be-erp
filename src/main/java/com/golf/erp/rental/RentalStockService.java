package com.golf.erp.rental;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.rental.RentalDtos.LossSource;
import com.golf.erp.rental.RentalDtos.StockResponse;
import com.golf.erp.rental.RentalDtos.StockTransactionRequest;
import com.golf.erp.rental.RentalDtos.StockTransactionResult;
import com.golf.erp.rental.RentalDtos.StockTxnResponse;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 골프장별 렌탈 재고. 모든 수량 변경은 {@link #move} / ADJUSTMENT 를 거치며,
 * 재고 행을 PESSIMISTIC_WRITE 로 잠근 뒤 바꾸고 rental_equipment_stock_txn 에 한 줄을 남긴다.
 */
@Service
@Transactional
public class RentalStockService {
	private final RentalEquipmentRepository equipmentRepo;
	private final RentalEquipmentStockRepository stockRepo;
	private final RentalEquipmentStockTxnRepository txnRepo;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public RentalStockService(RentalEquipmentRepository equipmentRepo, RentalEquipmentStockRepository stockRepo,
			RentalEquipmentStockTxnRepository txnRepo, RefChecker refs, AuditLogger audit, Clock clock) {
		this.equipmentRepo = equipmentRepo;
		this.stockRepo = stockRepo;
		this.txnRepo = txnRepo;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * 한 골프장의 품목별 재고. 활성 품목은 입고 전이라도 0 으로 보여 주고, 비활성 품목은 재고 행이 있을 때만(includeInactive=true 면 모두).
	 */
	@Transactional(readOnly = true)
	public List<StockResponse> courseStocks(long golfCourseId, EquipmentCategory category, boolean includeInactive) {
		refs.require(Ref.GOLF_COURSE, golfCourseId);
		Map<Long, RentalEquipmentStock> stocks = stockRepo.findByGolfCourseId(golfCourseId).stream()
				.collect(Collectors.toMap(RentalEquipmentStock::getRentalEquipmentId, Function.identity()));
		Specification<RentalEquipment> spec = Specification.allOf(Specs.eq("category", category));
		return equipmentRepo.findAll(spec, Sort.by("category", "code")).stream()
				.filter(e -> includeInactive || e.isActive() || stocks.containsKey(e.getId()))
				.map(e -> StockResponse.of(e, golfCourseId, stocks.get(e.getId()))).toList();
	}

	/** 한 품목의 골프장별 재고(재고 행이 있는 골프장만) */
	@Transactional(readOnly = true)
	public List<StockResponse> equipmentStocks(long equipmentId) {
		RentalEquipment e = findEquipment(equipmentId);
		return stockRepo.findByRentalEquipmentIdOrderByGolfCourseId(equipmentId).stream()
				.map(s -> StockResponse.of(e, s.getGolfCourseId(), s)).toList();
	}

	/** 재고 직접 처리(입고·정비·분실·실사 조정). 규칙은 {@link StockTransactionRequest} 참고 */
	public StockTransactionResult transact(long equipmentId, long golfCourseId, StockTransactionRequest r) {
		RentalEquipment e = findEquipment(equipmentId);
		refs.require(Ref.GOLF_COURSE, golfCourseId);
		StockTxnType type = r.type();
		if (!type.isManual())
			throw new IllegalArgumentException("RENT_OUT·RETURN 은 렌탈 출고·반납 처리에서만 기록됩니다.");
		String reason = RentalEquipmentService.blankToNull(r.reason());
		RentalEquipmentStockTxn txn;
		if (type == StockTxnType.ADJUSTMENT) {
			if (r.targetAvailable() == null)
				throw new IllegalArgumentException("실사 조정은 조정 후 가용 수량(targetAvailable)을 입력해야 합니다.");
			txn = adjust(e, golfCourseId, r.targetAvailable(), reason);
		} else {
			if (r.quantity() == null)
				throw new IllegalArgumentException("수량(quantity)을 입력하세요.");
			StockMovement m = switch (type) {
			case STOCK_IN -> {
				if (!e.isActive())
					throw new ConflictException("비활성 장비는 입고할 수 없습니다: " + e.label());
				yield StockMovement.STOCK_IN;
			}
			case MAINTENANCE_OUT -> StockMovement.MAINTENANCE_OUT;
			case MAINTENANCE_IN -> StockMovement.MAINTENANCE_IN;
			case LOSS -> r.lossFrom() == LossSource.MAINTENANCE ? StockMovement.LOSS_FROM_MAINTENANCE
					: StockMovement.LOSS_FROM_AVAILABLE;
			default -> throw new IllegalArgumentException("처리할 수 없는 재고 변동 유형입니다: " + type);
			};
			if (m == StockMovement.LOSS_FROM_MAINTENANCE)
				reason = prefix("[정비 중 수량에서 차감]", reason);
			else if (m == StockMovement.LOSS_FROM_AVAILABLE)
				reason = prefix("[가용 수량에서 차감]", reason);
			txn = move(e, golfCourseId, m, r.quantity(), null, null, reason);
		}
		if (type == StockTxnType.LOSS || type == StockTxnType.ADJUSTMENT) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("golfCourseId", golfCourseId);
			details.put("type", type);
			details.put("quantity", txn.getQuantity());
			details.put("reason", txn.getReason());
			audit.log("STOCK_" + type.name(), "rental_equipment_stock", equipmentId, details);
		}
		RentalEquipmentStock stock = stockRepo.findByRentalEquipmentIdAndGolfCourseId(equipmentId, golfCourseId)
				.orElseThrow();
		return new StockTransactionResult(StockResponse.of(e, golfCourseId, stock), StockTxnResponse.of(txn, e));
	}

	/** 재고 변동 기록 목록. from/to 는 발생일(occurred_at) 기준 */
	@Transactional(readOnly = true)
	public PageResult<StockTxnResponse> transactions(Long equipmentId, Long golfCourseId, StockTxnType type,
			String referenceType, Long referenceId, LocalDate from, LocalDate to, int page, int size) {
		Specification<RentalEquipmentStockTxn> spec = Specification.allOf(Specs.eq("rentalEquipmentId", equipmentId),
				Specs.eq("golfCourseId", golfCourseId), Specs.eq("type", type),
				Specs.eq("referenceType", referenceType), Specs.eq("referenceId", referenceId),
				Specs.gte("occurredAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("occurredAt", to == null ? null : to.plusDays(1).atStartOfDay()));
		Page<RentalEquipmentStockTxn> result = txnRepo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
		Map<Long, RentalEquipment> equipment = new HashMap<>();
		equipmentRepo.findAllById(result.getContent().stream().map(RentalEquipmentStockTxn::getRentalEquipmentId)
				.distinct().toList()).forEach(e -> equipment.put(e.getId(), e));
		return PageResult.of(result, t -> StockTxnResponse.of(t, equipment.get(t.getRentalEquipmentId())));
	}

	/**
	 * 재고 행을 잠그고 수량을 옮긴 뒤 변동 기록을 남긴다. 입고(STOCK_IN)면 재고 행이 없을 때 0 수량으로 먼저 만든다.
	 * 여러 품목을 옮길 때 호출자는 rental_equipment_id 오름차순으로 호출해 잠금 순서를 맞춘다(교착 방지).
	 *
	 * @throws ConflictException 재고 행이 없거나 수량이 모자랄 때
	 */
	RentalEquipmentStockTxn move(RentalEquipment e, long golfCourseId, StockMovement m, int quantity,
			String referenceType, Long referenceId, String reason) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalEquipmentStock stock = lockStock(e, golfCourseId, m == StockMovement.STOCK_IN, now);
		try {
			stock.apply(m, quantity, now);
		} catch (ConflictException ex) {
			throw new ConflictException(e.label() + ": " + ex.getMessage());
		}
		return txnRepo.save(new RentalEquipmentStockTxn(e.getId(), golfCourseId, m.txnType, quantity, referenceType,
				referenceId, reason, CurrentUser.idOrNull(), now));
	}

	/** 실사 조정: 가용 수량을 target 으로, 차이만큼 총 수량 증감. 기록 수량은 차이의 절댓값, 방향은 reason 앞머리에 남긴다. */
	private RentalEquipmentStockTxn adjust(RentalEquipment e, long golfCourseId, int target, String reason) {
		LocalDateTime now = LocalDateTime.now(clock);
		RentalEquipmentStock stock = lockStock(e, golfCourseId, target > 0, now);
		int before = stock.getAvailableQuantity();
		int delta = stock.adjustAvailableTo(target, now);
		String note = prefix("[실사 조정 가용 " + before + "→" + target + " (" + (delta > 0 ? "+" : "") + delta + ")]",
				reason);
		return txnRepo.save(new RentalEquipmentStockTxn(e.getId(), golfCourseId, StockTxnType.ADJUSTMENT,
				Math.abs(delta), null, null, note, CurrentUser.idOrNull(), now));
	}

	private RentalEquipmentStock lockStock(RentalEquipment e, long golfCourseId, boolean createIfAbsent,
			LocalDateTime now) {
		if (createIfAbsent)
			stockRepo.insertEmptyIfAbsent(e.getId(), golfCourseId, now);
		return stockRepo.findForUpdate(e.getId(), golfCourseId).orElseThrow(() -> new ConflictException(
				e.label() + ": 이 골프장에 등록된 재고가 없습니다. 먼저 입고 처리하세요."));
	}

	private RentalEquipment findEquipment(long id) {
		return equipmentRepo.findById(id).orElseThrow(() -> NotFoundException.of("렌탈 장비", id));
	}

	private static String prefix(String head, String reason) {
		return reason == null ? head : head + " " + reason;
	}
}
