package com.golf.erp.inventory;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.inventory.InventoryDtos.ItemRequest;
import com.golf.erp.inventory.InventoryDtos.ItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class InventoryItemService {
	private static final String DEFAULT_UNIT = "EA";

	private final InventoryItemRepository repo;
	private final InventoryTransactionRepository txs;
	private final AuditLogger audit;

	public InventoryItemService(InventoryItemRepository repo, InventoryTransactionRepository txs, AuditLogger audit) {
		this.repo = repo;
		this.txs = txs;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public PageResult<ItemResponse> list(Boolean active, String keyword, int page, int size) {
		Specification<InventoryItem> spec = Specification.allOf(Specs.eq("active", active),
				Specs.like(keyword, "itemCode", "itemName"));
		Page<InventoryItem> result = repo.findAll(spec, Pages.of(page, size, Sort.by("itemCode")));
		Map<Long, BigDecimal> stocks = txs.stocksOf(result.getContent().stream().map(InventoryItem::getId).toList());
		return PageResult.of(result, i -> ItemResponse.of(i, stocks.get(i.getId())));
	}

	/** 사용 중이고 재주문 기준(>0)이 있는 품목 중 현재고가 기준 이하인 것(품목 코드순) */
	@Transactional(readOnly = true)
	public List<ItemResponse> lowStock() {
		List<InventoryItem> candidates = repo.findByActiveTrueAndReorderLevelGreaterThanOrderByItemCode(BigDecimal.ZERO);
		Map<Long, BigDecimal> stocks = txs.stocksOf(candidates.stream().map(InventoryItem::getId).toList());
		return candidates.stream().map(i -> ItemResponse.of(i, stocks.get(i.getId())))
				.filter(ItemResponse::belowReorder).toList();
	}

	@Transactional(readOnly = true)
	public ItemResponse get(long id) {
		InventoryItem i = find(id);
		return ItemResponse.of(i, txs.stockOf(id));
	}

	public ItemResponse create(ItemRequest r) {
		String code = r.itemCode().trim();
		if (repo.existsByItemCode(code))
			throw new ConflictException("이미 있는 재고 품목 코드입니다: " + code);
		InventoryItem i = new InventoryItem(code);
		apply(i, code, r);
		return ItemResponse.of(repo.save(i), BigDecimal.ZERO);
	}

	public ItemResponse update(long id, ItemRequest r) {
		InventoryItem i = find(id);
		String code = r.itemCode().trim();
		if (repo.existsByItemCodeAndIdNot(code, id))
			throw new ConflictException("이미 있는 재고 품목 코드입니다: " + code);
		apply(i, code, r);
		return ItemResponse.of(i, txs.stockOf(id));
	}

	/** 비활성 품목은 새 수불·발주에 쓸 수 없다. 수불 이력은 그대로 남는다. */
	public ItemResponse changeActive(long id, boolean active) {
		InventoryItem i = find(id);
		if (i.isActive() != active) {
			i.changeActive(active);
			audit.log(active ? "ACTIVATE" : "DEACTIVATE", "inventory_item", id, Map.of("itemCode", i.getItemCode()));
		}
		return ItemResponse.of(i, txs.stockOf(id));
	}

	private void apply(InventoryItem i, String code, ItemRequest r) {
		String unit = r.unitName() == null || r.unitName().isBlank() ? DEFAULT_UNIT : r.unitName().trim();
		BigDecimal reorder = r.reorderLevel() == null ? BigDecimal.ZERO : r.reorderLevel();
		i.update(code, r.itemName().trim(), unit, reorder);
	}

	private InventoryItem find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("재고 품목", id));
	}
}
