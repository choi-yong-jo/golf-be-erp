package com.golf.erp.store;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * golf.store_order. 매장 고객 주문. 생성·수정 시각 컬럼이 없다(ordered_at 이 접수 시각).
 * <ul>
 * <li>금액: subtotal = Σ(취소되지 않은 항목의 line_amount), total_amount = subtotal − discount_amount
 * (ck_store_order_amount). 항목이 바뀔 때마다 {@link #recalculate()} 로 다시 계산한다.</li>
 * <li>진행: PLACED → ACCEPTED(accepted_at) → PREPARING → READY → COMPLETED(completed_at), 한 단계씩.
 * COMPLETED 가 아닌 상태에서는 사유와 함께 CANCELLED(cancelled_at) 로 바꿀 수 있다.</li>
 * <li>결제: UNPAID → PAID(paid_at) → REFUNDED. 결제된 주문을 취소하면 같은 처리에서 REFUNDED 로 바뀐다.</li>
 * <li>항목 추가·취소는 PLACED/ACCEPTED/PREPARING 이고 미결제(UNPAID)일 때만 — 결제 금액과 주문 금액이 어긋나지 않게 한다.</li>
 * </ul>
 */
@Entity
@Table(name = "store_order")
public class StoreOrder {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_order_id")
	private Long id;
	@Column(name = "store_id", nullable = false, updatable = false)
	private Long storeId;
	@Column(name = "order_no", nullable = false, length = 40)
	private String orderNo;
	@Column(name = "member_id", updatable = false)
	private Long memberId;
	@Column(name = "round_id", updatable = false)
	private Long roundId;
	@Column(name = "customer_name", length = 100, updatable = false)
	private String customerName;
	@Column(name = "customer_phone", length = 30, updatable = false)
	private String customerPhone;
	@Enumerated(EnumType.STRING)
	@Column(name = "order_type", nullable = false, length = 20, updatable = false)
	private StoreOrderType orderType = StoreOrderType.DINE_IN;
	@Enumerated(EnumType.STRING)
	@Column(name = "order_status", nullable = false, length = 20)
	private StoreOrderStatus status = StoreOrderStatus.PLACED;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private StorePaymentStatus paymentStatus = StorePaymentStatus.UNPAID;
	@Column(name = "subtotal", nullable = false, precision = 14, scale = 2)
	private BigDecimal subtotal = zero();
	@Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal discountAmount = zero();
	@Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalAmount = zero();
	@Column(name = "ordered_at", nullable = false, updatable = false)
	private LocalDateTime orderedAt;
	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Column(name = "paid_at")
	private LocalDateTime paidAt;
	@Column(name = "special_request", length = 1000, updatable = false)
	private String specialRequest;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;

	/** 주문 항목. DB 에 ON DELETE CASCADE 가 있고, 항목은 지우지 않고 CANCELLED 로 남긴다(orphanRemoval 없음). */
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
	@OrderBy("id")
	@BatchSize(size = 100)
	private List<StoreOrderItem> items = new ArrayList<>();

	protected StoreOrder() {
	}

	/** 번호는 임시 값으로 넣고, 저장(flush) 후 {@link #assignOrderNo(String)} 로 바꾼다. */
	public StoreOrder(Long storeId, String temporaryOrderNo, StoreOrderType orderType, Long memberId, Long roundId,
			String customerName, String customerPhone, String specialRequest, LocalDateTime now) {
		this.storeId = storeId;
		this.orderNo = temporaryOrderNo;
		this.orderType = orderType == null ? StoreOrderType.DINE_IN : orderType;
		this.memberId = memberId;
		this.roundId = roundId;
		this.customerName = customerName;
		this.customerPhone = customerPhone;
		this.specialRequest = specialRequest;
		this.orderedAt = now;
	}

	void assignOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	/** 메뉴 한 줄 추가(주문 시점 메뉴명·판매가 복사). 금액을 다시 계산한다. */
	StoreOrderItem addItem(Long storeMenuItemId, String menuName, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal discountAmount, String itemRequest) {
		requireItemsEditable();
		StoreOrderItem item = new StoreOrderItem(this, storeMenuItemId, menuName, quantity, unitPrice,
				discountAmount, itemRequest);
		items.add(item);
		recalculate();
		return item;
	}

	/**
	 * 항목 취소. 남은 항목이 하나도 없게 되면 주문 전체 취소를 쓰도록 409.
	 *
	 * @param newOrderDiscount null 이 아니면 주문 할인을 이 금액으로 바꾼다(취소 후 소계보다 할인이 커질 때 함께 조정)
	 */
	StoreOrderItem cancelItem(long itemId, BigDecimal newOrderDiscount) {
		requireItemsEditable();
		StoreOrderItem item = item(itemId);
		if (!item.isCancelled() && items.stream().filter(i -> !i.isCancelled()).count() == 1)
			throw new ConflictException("주문의 마지막 항목은 취소할 수 없습니다. 주문 전체를 취소하세요.");
		item.cancel();
		if (newOrderDiscount != null)
			this.discountAmount = checkDiscount(newOrderDiscount);
		recalculate();
		return item;
	}

	/** 주문 할인 적용. 0 이상 원 단위, 소계 이하(넘으면 400). */
	void applyDiscount(BigDecimal discount) {
		BigDecimal d = checkDiscount(discount == null ? BigDecimal.ZERO : discount);
		if (d.compareTo(subtotal) > 0)
			throw new IllegalArgumentException(
					"주문 할인 금액(" + plain(d) + ")이 소계(" + plain(subtotal) + ")보다 클 수 없습니다.");
		this.discountAmount = d;
		recalculate();
	}

	/** 항목 조리 진행. 완료·취소된 주문은 바꿀 수 없다. */
	StoreOrderItem changeItemStatus(long itemId, StoreOrderItemStatus target) {
		if (status.isFinal())
			throw new ConflictException("완료·취소된 주문의 항목 상태는 바꿀 수 없습니다. 현재: " + status);
		StoreOrderItem item = item(itemId);
		item.advance(target);
		return item;
	}

	/** 정상 흐름의 바로 다음 단계로만 바꾼다(ACCEPTED → accepted_at, COMPLETED → completed_at 기록). */
	void advance(StoreOrderStatus target, LocalDateTime now) {
		if (target == StoreOrderStatus.CANCELLED)
			throw new IllegalArgumentException("주문 취소는 주문 취소 API 로 처리하세요(사유 필요).");
		if (status.next() != target)
			throw new ConflictException("주문 상태를 " + status + " 에서 " + target + " (으)로 바꿀 수 없습니다."
					+ (status.next() == null ? "" : " 다음 단계: " + status.next()));
		this.status = target;
		if (target == StoreOrderStatus.ACCEPTED)
			this.acceptedAt = now;
		else if (target == StoreOrderStatus.COMPLETED)
			this.completedAt = now;
	}

	/**
	 * 주문 취소(COMPLETED·CANCELLED 제외). 결제된(PAID) 주문은 같은 처리에서 환불(REFUNDED)로 바꾼다.
	 * 항목 상태와 금액은 취소 당시 그대로 남긴다(환불 금액 = total_amount).
	 *
	 * @return 이 처리에서 환불도 했으면 true
	 */
	boolean cancel(String reason, LocalDateTime now) {
		if (status.isFinal())
			throw new ConflictException("완료·취소된 주문은 취소할 수 없습니다. 현재: " + status);
		this.status = StoreOrderStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancelReason = reason;
		if (paymentStatus == StorePaymentStatus.PAID) {
			this.paymentStatus = StorePaymentStatus.REFUNDED;
			return true;
		}
		return false;
	}

	/** 결제(UNPAID → PAID). 취소된 주문은 결제할 수 없다. */
	void pay(LocalDateTime now) {
		if (status == StoreOrderStatus.CANCELLED)
			throw new ConflictException("취소된 주문은 결제할 수 없습니다.");
		if (paymentStatus != StorePaymentStatus.UNPAID)
			throw new ConflictException("미결제(UNPAID) 주문만 결제할 수 있습니다. 현재: " + paymentStatus);
		this.paymentStatus = StorePaymentStatus.PAID;
		this.paidAt = now;
	}

	/** 환불(PAID → REFUNDED). paid_at 은 원래 결제 시각으로 남긴다. */
	void refund() {
		if (paymentStatus != StorePaymentStatus.PAID)
			throw new ConflictException("결제(PAID)된 주문만 환불할 수 있습니다. 현재: " + paymentStatus);
		this.paymentStatus = StorePaymentStatus.REFUNDED;
	}

	/** 소계·합계 재계산. 주문 할인이 소계보다 크면 409(할인 조정 필요). */
	private void recalculate() {
		BigDecimal sum = items.stream().filter(i -> !i.isCancelled()).map(StoreOrderItem::getLineAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
		if (discountAmount.compareTo(sum) > 0)
			throw new ConflictException("주문 할인 금액(" + plain(discountAmount) + ")이 소계(" + plain(sum)
					+ ")보다 클 수 없습니다. 주문 할인 금액을 함께 조정하세요.");
		this.subtotal = sum;
		this.totalAmount = sum.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal checkDiscount(BigDecimal discount) {
		if (discount.signum() < 0 || !StoreOrderItem.isWhole(discount))
			throw new IllegalArgumentException("주문 할인 금액은 0 이상 원 단위여야 합니다.");
		return discount.setScale(2, RoundingMode.HALF_UP);
	}

	private void requireItemsEditable() {
		if (!status.itemsEditable())
			throw new ConflictException("접수·확인·준비 중(PLACED/ACCEPTED/PREPARING)인 주문만 항목을 바꿀 수 있습니다. 현재: " + status);
		if (paymentStatus != StorePaymentStatus.UNPAID)
			throw new ConflictException("결제된 주문은 항목을 바꿀 수 없습니다. 먼저 환불하세요. 현재: " + paymentStatus);
	}

	private StoreOrderItem item(long itemId) {
		return items.stream().filter(i -> i.getId() != null && i.getId() == itemId).findFirst()
				.orElseThrow(() -> NotFoundException.of("주문 항목", itemId));
	}

	private static BigDecimal zero() {
		return BigDecimal.ZERO.setScale(2);
	}

	private static String plain(BigDecimal v) {
		return v.stripTrailingZeros().toPlainString();
	}

	public Long getId() {
		return id;
	}

	public Long getStoreId() {
		return storeId;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public Long getMemberId() {
		return memberId;
	}

	public Long getRoundId() {
		return roundId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public String getCustomerPhone() {
		return customerPhone;
	}

	public StoreOrderType getOrderType() {
		return orderType;
	}

	public StoreOrderStatus getStatus() {
		return status;
	}

	public StorePaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public BigDecimal getSubtotal() {
		return subtotal;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public LocalDateTime getOrderedAt() {
		return orderedAt;
	}

	public LocalDateTime getAcceptedAt() {
		return acceptedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public String getSpecialRequest() {
		return specialRequest;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public List<StoreOrderItem> getItems() {
		return Collections.unmodifiableList(items);
	}
}
