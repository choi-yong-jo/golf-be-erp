package com.golf.erp.inventory;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.inventory.InventoryDtos.PoLineRequest;
import com.golf.erp.inventory.InventoryDtos.PoRequest;
import com.golf.erp.inventory.InventoryDtos.PoResponse;
import com.golf.erp.inventory.InventoryDtos.PoSummary;
import com.golf.erp.inventory.InventoryDtos.ReceiveLine;
import com.golf.erp.inventory.InventoryDtos.ReceiveRequest;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 발주. 번호는 PO-yyyyMMdd-000123 (작성일 + id).
 * 입고(receive)는 줄마다 RECEIPT 수불을 발주 단가로 만들고, 전량 입고되면 RECEIVED, 아니면 PARTIAL.
 */
@Service
@Transactional
public class PurchaseOrderService {
	private static final String NO_PREFIX = "PO";

	private final PurchaseOrderRepository repo;
	private final InventoryItemRepository items;
	private final InventoryTransactionService transactions;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public PurchaseOrderService(PurchaseOrderRepository repo, InventoryItemRepository items,
			InventoryTransactionService transactions, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.items = items;
		this.transactions = transactions;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 발주번호·거래처, from/to: 발주일(ordered_at) */
	@Transactional(readOnly = true)
	public PageResult<PoSummary> list(PurchaseOrderStatus status, String keyword, LocalDate from, LocalDate to,
			int page, int size) {
		if (from != null && to != null && from.isAfter(to))
			throw new IllegalArgumentException("조회 시작일이 종료일보다 늦습니다.");
		Specification<PurchaseOrder> spec = Specification.allOf(Specs.eq("status", status),
				Specs.like(keyword, "poNo", "vendorName"), Specs.gte("orderedAt", from), Specs.lte("orderedAt", to));
		return PageResult.of(
				repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id")))),
				PoSummary::of);
	}

	@Transactional(readOnly = true)
	public PoResponse get(long id) {
		return toResponse(repo.findById(id).orElseThrow(() -> NotFoundException.of("발주서", id)));
	}

	public PoResponse create(PoRequest r) {
		LocalDate today = LocalDate.now(clock);
		PurchaseOrder po = new PurchaseOrder(DocumentNo.temporary(), today, CurrentUser.idOrNull());
		po.edit(r.vendorName().trim(), r.expectedAt(), blankToNull(r.memo()), lines(r.items()));
		repo.saveAndFlush(po);
		po.assignPoNo(DocumentNo.of(NO_PREFIX, today, po.getId()));
		return toResponse(po);
	}

	/** 작성 중(DRAFT) 발주서만. 품목 목록 전체를 요청 내용으로 바꾼다. */
	public PoResponse update(long id, PoRequest r) {
		PurchaseOrder po = lock(id);
		po.edit(r.vendorName().trim(), r.expectedAt(), blankToNull(r.memo()), lines(r.items()));
		repo.flush();
		return toResponse(po);
	}

	/** DRAFT → ORDERED. 발주일(ordered_at)을 오늘로 기록한다. */
	public PoResponse order(long id) {
		PurchaseOrder po = lock(id);
		po.order(LocalDate.now(clock));
		audit.log("ORDER", "purchase_order", id, Map.of("poNo", po.getPoNo(), "totalAmount", po.getTotalAmount()));
		return toResponse(po);
	}

	public PoResponse receive(long id, ReceiveRequest r) {
		PurchaseOrder po = lock(id);
		refs.requireIfPresent(Ref.FACILITY, r.facilityId());
		Set<Long> seen = new HashSet<>();
		for (ReceiveLine l : r.lines())
			if (!seen.add(l.purchaseOrderItemId()))
				throw new IllegalArgumentException("같은 발주 품목을 두 번 입고할 수 없습니다: " + l.purchaseOrderItemId());
		LocalDateTime now = LocalDateTime.now(clock);
		String memo = blankToNull(r.memo());
		List<Map<String, Object>> received = new ArrayList<>();
		for (ReceiveLine l : r.lines()) {
			PurchaseOrderItem item = po.receive(l.purchaseOrderItemId(), l.quantity());
			transactions.record(item.getInventoryItemId(), r.facilityId(), InventoryTransactionType.RECEIPT,
					l.quantity(), item.getUnitCost(), po.getPoNo(), now, memo == null ? "발주 입고" : memo, false);
			received.add(Map.of("purchaseOrderItemId", item.getId(), "quantity", l.quantity()));
		}
		po.refreshReceiveStatus();
		audit.log("RECEIVE", "purchase_order", id,
				Map.of("poNo", po.getPoNo(), "status", po.getStatus(), "lines", received));
		return toResponse(po);
	}

	/** DRAFT·ORDERED 이고 입고가 하나도 없을 때만 */
	public PoResponse cancel(long id) {
		PurchaseOrder po = lock(id);
		PurchaseOrderStatus before = po.getStatus();
		po.cancel();
		audit.log("CANCEL", "purchase_order", id, Map.of("poNo", po.getPoNo(), "before", before));
		return toResponse(po);
	}

	/** 품목 존재·사용 여부 확인 후 엔티티 입력값으로 바꾼다. */
	private List<PurchaseOrder.Line> lines(List<PoLineRequest> req) {
		Set<Long> ids = req.stream().map(PoLineRequest::inventoryItemId).collect(Collectors.toSet());
		if (ids.size() != req.size())
			throw new IllegalArgumentException("같은 품목을 두 줄로 넣을 수 없습니다.");
		Map<Long, InventoryItem> found = itemMap(ids);
		for (Long iid : ids) {
			InventoryItem i = found.get(iid);
			if (i == null)
				throw NotFoundException.of("재고 품목", iid);
			if (!i.isActive())
				throw new ConflictException("사용 중지된 재고 품목입니다: " + i.getItemName());
		}
		return req.stream().map(l -> new PurchaseOrder.Line(l.inventoryItemId(), l.orderedQuantity(), l.unitCost()))
				.toList();
	}

	private PoResponse toResponse(PurchaseOrder po) {
		return PoResponse.of(po,
				itemMap(po.getItems().stream().map(PurchaseOrderItem::getInventoryItemId).collect(Collectors.toSet())));
	}

	private Map<Long, InventoryItem> itemMap(Set<Long> ids) {
		Map<Long, InventoryItem> m = new HashMap<>();
		items.findAllById(ids).forEach(i -> m.put(i.getId(), i));
		return m;
	}

	private PurchaseOrder lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("발주서", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
