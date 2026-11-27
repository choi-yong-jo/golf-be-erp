package com.golf.erp.inventory;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * golf.purchase_order. total_amount = Σ(ordered_quantity × unit_cost).
 * ordered_at 은 작성일로 채우고(NOT NULL), 발주(order) 시 발주일로 다시 기록한다.
 */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "purchase_order_id")
	private Long id;
	@Column(name = "po_no", nullable = false, length = 40)
	private String poNo;
	@Column(name = "vendor_name", nullable = false, length = 150)
	private String vendorName;
	@Column(name = "ordered_at", nullable = false)
	private LocalDate orderedAt;
	@Column(name = "expected_at")
	private LocalDate expectedAt;
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;
	@Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalAmount = BigDecimal.ZERO;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(length = 500)
	private String memo;

	@OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<PurchaseOrderItem> items = new ArrayList<>();

	/** 발주서 한 줄 입력값 */
	public record Line(Long inventoryItemId, BigDecimal orderedQuantity, BigDecimal unitCost) {
	}

	protected PurchaseOrder() {
	}

	public PurchaseOrder(String poNo, LocalDate orderedAt, Long createdBy) {
		this.poNo = poNo;
		this.orderedAt = orderedAt;
		this.createdBy = createdBy;
	}

	void assignPoNo(String poNo) {
		this.poNo = poNo;
	}

	/**
	 * 작성 중(DRAFT) 발주서 내용 변경. 품목이 같은 줄은 수정, 빠진 줄은 삭제, 새 품목은 추가한다
	 * (삭제 후 같은 품목을 다시 넣으면 insert 가 delete 보다 먼저 실행돼 uq_po_item 위반이 나므로 줄을 재사용한다).
	 */
	public void edit(String vendorName, LocalDate expectedAt, String memo, List<Line> lines) {
		requireStatus(PurchaseOrderStatus.DRAFT, "작성 중(DRAFT)인 발주서만 수정할 수 있습니다.");
		this.vendorName = vendorName;
		this.expectedAt = expectedAt;
		this.memo = memo;
		Map<Long, Line> wanted = new HashMap<>();
		for (Line l : lines)
			if (wanted.put(l.inventoryItemId(), l) != null)
				throw new IllegalArgumentException("같은 품목을 두 줄로 넣을 수 없습니다: " + l.inventoryItemId());
		items.removeIf(i -> !wanted.containsKey(i.getInventoryItemId()));
		for (PurchaseOrderItem i : items) {
			Line l = wanted.remove(i.getInventoryItemId());
			i.change(l.orderedQuantity(), l.unitCost());
		}
		for (Line l : lines)
			if (wanted.containsKey(l.inventoryItemId()))
				items.add(new PurchaseOrderItem(this, l.inventoryItemId(), l.orderedQuantity(), l.unitCost()));
		totalAmount = items.stream().map(PurchaseOrderItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public void order(LocalDate today) {
		requireStatus(PurchaseOrderStatus.DRAFT, "작성 중(DRAFT)인 발주서만 발주할 수 있습니다.");
		if (items.isEmpty())
			throw new ConflictException("발주 품목이 없습니다.");
		status = PurchaseOrderStatus.ORDERED;
		orderedAt = today;
	}

	/** 한 줄 입고. 전체 상태는 {@link #refreshReceiveStatus()} 로 갱신한다. */
	public PurchaseOrderItem receive(long purchaseOrderItemId, BigDecimal quantity) {
		if (status != PurchaseOrderStatus.ORDERED && status != PurchaseOrderStatus.PARTIAL)
			throw new ConflictException("발주(ORDERED)·일부 입고(PARTIAL) 상태에서만 입고할 수 있습니다. 현재: " + status);
		PurchaseOrderItem item = items.stream().filter(i -> i.getId() != null && i.getId() == purchaseOrderItemId)
				.findFirst().orElseThrow(() -> NotFoundException.of("발주 품목", purchaseOrderItemId));
		item.receive(quantity);
		return item;
	}

	void refreshReceiveStatus() {
		status = items.stream().allMatch(PurchaseOrderItem::fullyReceived) ? PurchaseOrderStatus.RECEIVED
				: PurchaseOrderStatus.PARTIAL;
	}

	public void cancel() {
		if (status != PurchaseOrderStatus.DRAFT && status != PurchaseOrderStatus.ORDERED)
			throw new ConflictException("작성 중·발주 상태의 발주서만 취소할 수 있습니다. 현재: " + status);
		if (items.stream().anyMatch(i -> i.getReceivedQuantity().signum() > 0))
			throw new ConflictException("입고된 품목이 있는 발주서는 취소할 수 없습니다.");
		status = PurchaseOrderStatus.CANCELLED;
	}

	private void requireStatus(PurchaseOrderStatus expected, String message) {
		if (status != expected)
			throw new ConflictException(message + " 현재: " + status);
	}

	public Long getId() {
		return id;
	}

	public String getPoNo() {
		return poNo;
	}

	public String getVendorName() {
		return vendorName;
	}

	public LocalDate getOrderedAt() {
		return orderedAt;
	}

	public LocalDate getExpectedAt() {
		return expectedAt;
	}

	public PurchaseOrderStatus getStatus() {
		return status;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public String getMemo() {
		return memo;
	}

	public List<PurchaseOrderItem> getItems() {
		return Collections.unmodifiableList(items);
	}
}
