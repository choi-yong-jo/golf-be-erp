package com.golf.erp.store;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * golf.store_order_item. 주문 시점의 메뉴명·판매가를 복사해 보관한다(이후 메뉴 가격이 바뀌어도 그대로).
 * line_amount = quantity × unit_price − discount_amount 가 정확히 같아야 하므로(ck_store_order_item_amount)
 * 수량은 1 이상 정수, 단가·할인은 원 단위로만 받는다. 취소(CANCELLED)된 항목은 주문 소계에서 빠진다.
 */
@Entity
@Table(name = "store_order_item")
public class StoreOrderItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_order_item_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "store_order_id", nullable = false, updatable = false)
	private StoreOrder order;
	@Column(name = "store_menu_item_id", updatable = false)
	private Long storeMenuItemId;
	@Column(name = "menu_name_snapshot", nullable = false, length = 150, updatable = false)
	private String menuNameSnapshot;
	@Column(name = "quantity", nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal quantity = BigDecimal.ONE;
	@Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal unitPrice;
	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal discountAmount = BigDecimal.ZERO;
	@Column(name = "line_amount", nullable = false, precision = 14, scale = 2, updatable = false)
	private BigDecimal lineAmount;
	@Enumerated(EnumType.STRING)
	@Column(name = "item_status", nullable = false, length = 20)
	private StoreOrderItemStatus status = StoreOrderItemStatus.ORDERED;
	@Column(name = "item_request", length = 500, updatable = false)
	private String itemRequest;

	protected StoreOrderItem() {
	}

	/**
	 * @param quantity       1 이상 정수
	 * @param unitPrice      주문 시점 판매가(원 단위)
	 * @param discountAmount 항목 할인(원 단위, 0 ~ 수량×단가)
	 */
	StoreOrderItem(StoreOrder order, Long storeMenuItemId, String menuNameSnapshot, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal discountAmount, String itemRequest) {
		if (quantity == null || quantity.signum() <= 0 || !isWhole(quantity))
			throw new IllegalArgumentException("수량은 1 이상의 정수여야 합니다.");
		if (unitPrice == null || unitPrice.signum() < 0 || !isWhole(unitPrice))
			throw new IllegalArgumentException("단가는 0 이상 원 단위여야 합니다.");
		BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
		if (discount.signum() < 0 || !isWhole(discount))
			throw new IllegalArgumentException("항목 할인 금액은 0 이상 원 단위여야 합니다.");
		BigDecimal gross = quantity.multiply(unitPrice);
		if (discount.compareTo(gross) > 0)
			throw new IllegalArgumentException(
					"항목 할인 금액(" + plain(discount) + ")이 주문 금액(" + plain(gross) + ")보다 클 수 없습니다: " + menuNameSnapshot);
		this.order = order;
		this.storeMenuItemId = storeMenuItemId;
		this.menuNameSnapshot = menuNameSnapshot;
		this.quantity = quantity.setScale(2, RoundingMode.HALF_UP);
		this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
		this.discountAmount = discount.setScale(2, RoundingMode.HALF_UP);
		this.lineAmount = gross.subtract(discount).setScale(2, RoundingMode.HALF_UP);
		this.itemRequest = itemRequest;
	}

	/** 조리 진행(ORDERED → PREPARING → READY → SERVED). 앞 단계로 되돌리거나 같은 상태로는 바꿀 수 없다. */
	void advance(StoreOrderItemStatus target) {
		if (target == StoreOrderItemStatus.CANCELLED)
			throw new IllegalArgumentException("항목 취소는 항목 취소 API 로 처리하세요.");
		if (status == StoreOrderItemStatus.CANCELLED)
			throw new ConflictException("취소된 주문 항목은 상태를 바꿀 수 없습니다.");
		if (target.ordinal() <= status.ordinal())
			throw new ConflictException("주문 항목 상태를 " + status + " 에서 " + target + " (으)로 바꿀 수 없습니다.");
		this.status = target;
	}

	/** 제공 완료(SERVED)·이미 취소된 항목은 취소할 수 없다. */
	void cancel() {
		if (status == StoreOrderItemStatus.CANCELLED)
			throw new ConflictException("이미 취소된 주문 항목입니다.");
		if (status == StoreOrderItemStatus.SERVED)
			throw new ConflictException("제공 완료(SERVED)된 주문 항목은 취소할 수 없습니다.");
		this.status = StoreOrderItemStatus.CANCELLED;
	}

	boolean isCancelled() {
		return status == StoreOrderItemStatus.CANCELLED;
	}

	static boolean isWhole(BigDecimal v) {
		return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
	}

	private static String plain(BigDecimal v) {
		return v.stripTrailingZeros().toPlainString();
	}

	public Long getId() {
		return id;
	}

	public Long getStoreMenuItemId() {
		return storeMenuItemId;
	}

	public String getMenuNameSnapshot() {
		return menuNameSnapshot;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getLineAmount() {
		return lineAmount;
	}

	public StoreOrderItemStatus getStatus() {
		return status;
	}

	public String getItemRequest() {
		return itemRequest;
	}
}
