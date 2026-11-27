package com.golf.erp.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 매장 주문 API 요청/응답. 금액·수량은 원 단위·정수만 받는다(소수면 400). */
public final class StoreOrderDtos {
	private StoreOrderDtos() {
	}

	// ---- 요청 ----

	/**
	 * 주문 한 줄. quantity: 1 이상 정수(최대 999), discountAmount: 항목 할인(원 단위, 수량×판매가 이하, 생략 시 0).
	 * 단가는 요청으로 받지 않고 주문 시점 메뉴 판매가를 쓴다.
	 */
	public record OrderLineRequest(@NotNull Long menuItemId,
			@NotNull @Positive @DecimalMax("999") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal discountAmount,
			@Size(max = 500) String itemRequest) {
	}

	/**
	 * 주문 접수. 매장은 영업 중(ACTIVE)이어야 하고, 메뉴는 같은 매장의 판매 가능한 메뉴여야 한다.
	 * orderType 생략 시 DINE_IN, discountAmount: 주문 할인(원 단위, 소계 이하, 생략 시 0).
	 */
	public record StoreOrderCreateRequest(@NotNull Long storeId, StoreOrderType orderType, Long memberId,
			Long roundId, @Size(max = 100) String customerName, @Size(max = 30) String customerPhone,
			@Size(max = 1000) String specialRequest,
			@PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal discountAmount,
			@NotEmpty @Size(max = 100) List<@Valid @NotNull OrderLineRequest> items) {
	}

	/**
	 * 항목 취소. reason 은 감사 로그용(선택). orderDiscountAmount 를 주면 주문 할인을 그 금액으로 바꾼다
	 * (취소 후 소계가 주문 할인보다 작아질 때 함께 조정).
	 */
	public record CancelItemRequest(@Size(max = 500) String reason,
			@PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal orderDiscountAmount) {
	}

	/** 다음 단계로만: ACCEPTED, PREPARING, READY, COMPLETED. 취소는 cancel API */
	public record OrderStatusRequest(@NotNull StoreOrderStatus status) {
	}

	/** 앞 단계로만: PREPARING, READY, SERVED. 취소는 항목 취소 API */
	public record OrderItemStatusRequest(@NotNull StoreOrderItemStatus status) {
	}

	public record CancelOrderRequest(@NotBlank @Size(max = 500) String reason) {
	}

	/** 환불 사유(선택, 감사 로그용) */
	public record RefundRequest(@Size(max = 500) String reason) {
	}

	// ---- 응답 ----

	public record StoreOrderItemResponse(Long id, Long menuItemId, String menuName, BigDecimal quantity,
			BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal lineAmount, StoreOrderItemStatus status,
			String itemRequest) {

		static StoreOrderItemResponse of(StoreOrderItem i) {
			return new StoreOrderItemResponse(i.getId(), i.getStoreMenuItemId(), i.getMenuNameSnapshot(),
					i.getQuantity(), i.getUnitPrice(), i.getDiscountAmount(), i.getLineAmount(), i.getStatus(),
					i.getItemRequest());
		}
	}

	/** 목록 행(항목 제외). itemCount: 취소되지 않은 항목 수 */
	public record StoreOrderRow(Long id, String orderNo, Long storeId, String storeName, Long memberId, Long roundId,
			String customerName, String customerPhone, StoreOrderType orderType, StoreOrderStatus status,
			StorePaymentStatus paymentStatus, BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
			long itemCount, LocalDateTime orderedAt, LocalDateTime paidAt, LocalDateTime completedAt,
			LocalDateTime cancelledAt) {

		static StoreOrderRow of(StoreOrder o, String storeName) {
			long count = o.getItems().stream().filter(i -> !i.isCancelled()).count();
			return new StoreOrderRow(o.getId(), o.getOrderNo(), o.getStoreId(), storeName, o.getMemberId(),
					o.getRoundId(), o.getCustomerName(), o.getCustomerPhone(), o.getOrderType(), o.getStatus(),
					o.getPaymentStatus(), o.getSubtotal(), o.getDiscountAmount(), o.getTotalAmount(), count,
					o.getOrderedAt(), o.getPaidAt(), o.getCompletedAt(), o.getCancelledAt());
		}
	}

	/** 상세(항목 포함, 취소된 항목도 상태 CANCELLED 로 포함) */
	public record StoreOrderResponse(Long id, String orderNo, Long storeId, String storeName, Long golfCourseId,
			Long memberId, Long roundId, String customerName, String customerPhone, StoreOrderType orderType,
			StoreOrderStatus status, StorePaymentStatus paymentStatus, BigDecimal subtotal, BigDecimal discountAmount,
			BigDecimal totalAmount, LocalDateTime orderedAt, LocalDateTime acceptedAt, LocalDateTime completedAt,
			LocalDateTime paidAt, String specialRequest, LocalDateTime cancelledAt, String cancelReason,
			List<StoreOrderItemResponse> items) {

		static StoreOrderResponse of(StoreOrder o, TenantStore s) {
			return new StoreOrderResponse(o.getId(), o.getOrderNo(), o.getStoreId(),
					s == null ? null : s.getStoreName(), s == null ? null : s.getGolfCourseId(), o.getMemberId(),
					o.getRoundId(), o.getCustomerName(), o.getCustomerPhone(), o.getOrderType(), o.getStatus(),
					o.getPaymentStatus(), o.getSubtotal(), o.getDiscountAmount(), o.getTotalAmount(),
					o.getOrderedAt(), o.getAcceptedAt(), o.getCompletedAt(), o.getPaidAt(), o.getSpecialRequest(),
					o.getCancelledAt(), o.getCancelReason(),
					o.getItems().stream().map(StoreOrderItemResponse::of).toList());
		}
	}

	// ---- 매출 요약 ----

	/**
	 * 하루 집계. orderCount·cancelledCount 는 접수일(ordered_at) 기준,
	 * paid*·refunded* 는 결제일(paid_at) 기준 — paidTotal 은 현재 PAID 인 주문, refundedTotal 은 결제 후 환불된 주문 합계.
	 */
	public record DailySales(LocalDate date, long orderCount, long cancelledCount, long paidCount,
			BigDecimal paidTotal, long refundedCount, BigDecimal refundedTotal) {
	}

	/** 매장별 기간 합계 + 날짜별 집계(값이 있는 날만, 날짜 순) */
	public record StoreSales(Long storeId, String storeCode, String storeName, Long golfCourseId, long orderCount,
			long cancelledCount, long paidCount, BigDecimal paidTotal, long refundedCount, BigDecimal refundedTotal,
			List<DailySales> days) {
	}

	public record StoreSalesSummaryResponse(LocalDate from, LocalDate to, long orderCount, long cancelledCount,
			long paidCount, BigDecimal paidTotal, long refundedCount, BigDecimal refundedTotal,
			List<StoreSales> stores) {
	}
}
