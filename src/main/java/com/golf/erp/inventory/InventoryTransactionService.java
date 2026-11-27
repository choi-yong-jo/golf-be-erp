package com.golf.erp.inventory;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.inventory.InventoryDtos.TransactionRequest;
import com.golf.erp.inventory.InventoryDtos.TransactionResponse;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 재고 수불 등록·조회. 수불은 원장이라 수정·삭제 API 가 없다(정정은 반대 방향 거래로 남긴다).
 * <p>
 * 실사 조정 규칙: DDL 에 부호 컬럼이 없고 quantity &gt; 0 이므로 요청의 direction 으로 나눈다.
 * INCREASE → transaction_type=ADJUSTMENT, DECREASE → transaction_type=ISSUE + memo "[조정] ...".
 * 그래서 현재고 공식은 항상 Σ(ISSUE 외) − Σ(ISSUE) 하나로 유지된다.
 * <p>
 * 재고를 줄이는 거래(ISSUE, 조정 감소)는 품목 행을 PESSIMISTIC_WRITE 로 잠근 뒤 현재고를 확인해
 * 음수 재고를 막는다.
 */
@Service
@Transactional
public class InventoryTransactionService {
	private final InventoryTransactionRepository repo;
	private final InventoryItemRepository items;
	private final RefChecker refs;
	private final Clock clock;

	public InventoryTransactionService(InventoryTransactionRepository repo, InventoryItemRepository items,
			RefChecker refs, Clock clock) {
		this.repo = repo;
		this.items = items;
		this.refs = refs;
		this.clock = clock;
	}

	/**
	 * @param adjustmentOnly true 면 실사 조정 거래만(ADJUSTMENT + memo 가 "[조정]" 으로 시작하는 ISSUE)
	 * @param from/to        발생 일자(occurred_at) 범위, 양 끝 포함
	 */
	@Transactional(readOnly = true)
	public PageResult<TransactionResponse> list(Long inventoryItemId, Long facilityId, InventoryTransactionType type,
			Boolean adjustmentOnly, LocalDate from, LocalDate to, int page, int size) {
		if (from != null && to != null && from.isAfter(to))
			throw new IllegalArgumentException("조회 시작일이 종료일보다 늦습니다.");
		Specification<InventoryTransaction> spec = Specification.allOf(Specs.eq("inventoryItemId", inventoryItemId),
				Specs.eq("facilityId", facilityId), Specs.eq("type", type),
				Specs.gte("occurredAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("occurredAt", to == null ? null : to.plusDays(1).atStartOfDay()));
		if (Boolean.TRUE.equals(adjustmentOnly))
			spec = spec.and((r, q, cb) -> cb.or(cb.equal(r.get("type"), InventoryTransactionType.ADJUSTMENT),
					cb.and(cb.equal(r.get("type"), InventoryTransactionType.ISSUE),
							cb.like(r.<String>get("memo"), InventoryTransactionType.ADJUSTMENT_MEMO_PREFIX + "%"))));
		Page<InventoryTransaction> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
		Map<Long, InventoryItem> names = new HashMap<>();
		items.findAllById(result.getContent().stream().map(InventoryTransaction::getInventoryItemId)
				.collect(Collectors.toSet())).forEach(i -> names.put(i.getId(), i));
		return PageResult.of(result, t -> TransactionResponse.of(t, names.get(t.getInventoryItemId())));
	}

	@Transactional(readOnly = true)
	public TransactionResponse get(long id) {
		InventoryTransaction t = repo.findById(id).orElseThrow(() -> NotFoundException.of("재고 수불", id));
		return TransactionResponse.of(t, items.findById(t.getInventoryItemId()).orElse(null));
	}

	public TransactionResponse create(TransactionRequest r) {
		InventoryTransactionType type = r.type();
		String memo = blankToNull(r.memo());
		if (type == InventoryTransactionType.ADJUSTMENT) {
			if (r.direction() == null)
				throw new IllegalArgumentException("조정(ADJUSTMENT)은 방향(direction: INCREASE/DECREASE)이 필요합니다.");
			if (r.direction() == AdjustmentDirection.DECREASE) {
				type = InventoryTransactionType.ISSUE;
				memo = adjustmentMemo(memo);
			}
		} else if (r.direction() != null) {
			throw new IllegalArgumentException("direction 은 조정(ADJUSTMENT)에만 지정할 수 있습니다.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime occurredAt = r.occurredAt() == null ? now : r.occurredAt();
		if (occurredAt.isAfter(now))
			throw new IllegalArgumentException("발생 시각은 미래일 수 없습니다.");
		refs.requireIfPresent(Ref.FACILITY, r.facilityId());
		BigDecimal unitCost = r.unitCost() == null ? BigDecimal.ZERO : r.unitCost().setScale(2, RoundingMode.HALF_UP);
		InventoryTransaction t = record(r.inventoryItemId(), r.facilityId(), type, r.quantity(), unitCost,
				blankToNull(r.referenceNo()), occurredAt, memo, true);
		return TransactionResponse.of(t, items.findById(t.getInventoryItemId()).orElse(null));
	}

	/**
	 * 수불 한 건 기록(발주 입고에서도 쓴다). 감소 거래는 품목 행을 잠근 뒤 현재고가 음수가 되지 않는지 확인한다.
	 *
	 * @param requireActive true 면 사용 중지된 품목을 거부한다(발주 입고는 이미 발주된 물건이라 false)
	 */
	public InventoryTransaction record(Long itemId, Long facilityId, InventoryTransactionType type, BigDecimal quantity,
			BigDecimal unitCost, String referenceNo, LocalDateTime occurredAt, String memo, boolean requireActive) {
		InventoryItem item = type.increases()
				? items.findById(itemId).orElseThrow(() -> NotFoundException.of("재고 품목", itemId))
				: items.findByIdForUpdate(itemId).orElseThrow(() -> NotFoundException.of("재고 품목", itemId));
		if (requireActive && !item.isActive())
			throw new ConflictException("사용 중지된 재고 품목입니다: " + item.getItemName());
		if (!type.increases()) {
			BigDecimal stock = repo.stockOf(itemId);
			if (stock.compareTo(quantity) < 0)
				throw new ConflictException("재고가 부족합니다. 현재고 " + stock.stripTrailingZeros().toPlainString() + " "
						+ item.getUnitName() + ", 요청 " + quantity.stripTrailingZeros().toPlainString());
		}
		return repo.save(new InventoryTransaction(itemId, facilityId, type, quantity, unitCost, referenceNo,
				occurredAt, CurrentUser.idOrNull(), memo));
	}

	private static String adjustmentMemo(String memo) {
		String m = InventoryTransactionType.ADJUSTMENT_MEMO_PREFIX + (memo == null ? "" : " " + memo);
		return m.length() > 500 ? m.substring(0, 500) : m;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
