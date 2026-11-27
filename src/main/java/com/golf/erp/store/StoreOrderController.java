package com.golf.erp.store;

import com.golf.erp.common.PageResult;
import com.golf.erp.store.StoreOrderDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 매장 주문. 권한은 매장 주문(STORE_ORDER) 모듈 — /api/store-orders/** */
@Tag(name = "매장 주문")
@RestController
@RequestMapping("/api/store-orders")
public class StoreOrderController {
	private final StoreOrderService orders;

	public StoreOrderController(StoreOrderService orders) {
		this.orders = orders;
	}

	@Operation(summary = "매장 주문 목록", description = "접수 시각 최근 순. from/to: 접수일(ordered_at). "
			+ "golfCourseId: 매장이 속한 골프장. keyword: 주문번호·고객명·고객 연락처")
	@GetMapping
	public PageResult<StoreOrderRow> list(@RequestParam(required = false) Long storeId,
			@RequestParam(required = false) Long golfCourseId, @RequestParam(required = false) StoreOrderStatus status,
			@RequestParam(required = false) StorePaymentStatus paymentStatus,
			@RequestParam(required = false) StoreOrderType orderType,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long memberId, @RequestParam(required = false) Long roundId,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return orders.list(storeId, golfCourseId, status, paymentStatus, orderType, from, to, memberId, roundId,
				keyword, page, size);
	}

	@Operation(summary = "매장 매출 요약", description = "매장별·날짜별. from/to 생략 시 오늘, 최대 366일. "
			+ "주문 수는 접수일, 결제·환불 금액은 결제일(paid_at) 기준. paidTotal: 현재 결제 상태 주문 합계(순매출)")
	@GetMapping("/summary")
	public StoreSalesSummaryResponse summary(@RequestParam(required = false) Long storeId,
			@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return orders.summary(storeId, golfCourseId, from, to);
	}

	@Operation(summary = "매장 주문 상세", description = "항목 포함(취소된 항목도 CANCELLED 로 포함)")
	@GetMapping("/{id}")
	public StoreOrderResponse get(@PathVariable long id) {
		return orders.get(id);
	}

	@Operation(summary = "매장 주문 접수", description = "매장은 영업 중(ACTIVE), 메뉴는 같은 매장의 판매 가능 메뉴. "
			+ "단가는 주문 시점 판매가. 수량은 1 이상 정수, 금액은 원 단위(소수면 400). 주문번호 SO-yyyyMMdd-id")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public StoreOrderResponse create(@Valid @RequestBody StoreOrderCreateRequest r) {
		return orders.create(r);
	}

	@Operation(summary = "주문 항목 추가", description = "접수·확인·준비 중(PLACED/ACCEPTED/PREPARING)이고 미결제일 때만")
	@PostMapping("/{id}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public StoreOrderResponse addItem(@PathVariable long id, @Valid @RequestBody OrderLineRequest r) {
		return orders.addItem(id, r);
	}

	@Operation(summary = "주문 항목 취소", description = "접수·확인·준비 중이고 미결제일 때만. 제공 완료 항목·마지막 남은 항목은 불가(409). "
			+ "취소 후 소계가 주문 할인보다 작아지면 orderDiscountAmount 로 할인을 함께 조정")
	@PostMapping("/{id}/items/{itemId}/cancel")
	public StoreOrderResponse cancelItem(@PathVariable long id, @PathVariable long itemId,
			@Valid @RequestBody(required = false) CancelItemRequest r) {
		return orders.cancelItem(id, itemId, r);
	}

	@Operation(summary = "주문 항목 상태 변경", description = "ORDERED → PREPARING → READY → SERVED (앞으로만). 완료·취소 주문은 불가")
	@PatchMapping("/{id}/items/{itemId}/status")
	public StoreOrderResponse changeItemStatus(@PathVariable long id, @PathVariable long itemId,
			@Valid @RequestBody OrderItemStatusRequest r) {
		return orders.changeItemStatus(id, itemId, r.status());
	}

	@Operation(summary = "주문 상태 변경", description = "다음 단계로만: PLACED → ACCEPTED → PREPARING → READY → COMPLETED. "
			+ "취소는 cancel API")
	@PatchMapping("/{id}/status")
	public StoreOrderResponse changeStatus(@PathVariable long id, @Valid @RequestBody OrderStatusRequest r) {
		return orders.changeStatus(id, r.status());
	}

	@Operation(summary = "주문 취소", description = "완료(COMPLETED)가 아닌 주문, 사유 필수. 결제된 주문은 같은 처리에서 환불(REFUNDED)된다")
	@PostMapping("/{id}/cancel")
	public StoreOrderResponse cancel(@PathVariable long id, @Valid @RequestBody CancelOrderRequest r) {
		return orders.cancel(id, r.reason());
	}

	@Operation(summary = "주문 결제", description = "UNPAID → PAID. 취소된 주문은 불가(409)")
	@PostMapping("/{id}/pay")
	public StoreOrderResponse pay(@PathVariable long id) {
		return orders.pay(id);
	}

	@Operation(summary = "주문 환불", description = "PAID → REFUNDED. 주문 진행 상태는 그대로")
	@PostMapping("/{id}/refund")
	public StoreOrderResponse refund(@PathVariable long id, @Valid @RequestBody(required = false) RefundRequest r) {
		return orders.refund(id, r == null ? null : r.reason());
	}
}
