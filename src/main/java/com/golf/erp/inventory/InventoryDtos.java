package com.golf.erp.inventory;

import jakarta.validation.Valid;
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
import java.util.Map;

/** 재고 품목·수불·발주 API 요청/응답 */
public final class InventoryDtos {
	private InventoryDtos() {
	}

	public record ActiveRequest(@NotNull Boolean active) {
	}

	// ---- 재고 품목 ----

	/** unitName 을 비우면 EA, reorderLevel 을 비우면 0(재주문 관리 안 함) */
	public record ItemRequest(@NotBlank @Size(max = 40) String itemCode, @NotBlank @Size(max = 150) String itemName,
			@Size(max = 30) String unitName,
			@PositiveOrZero @Digits(integer = 9, fraction = 3) BigDecimal reorderLevel) {
	}

	/** currentStock: 수불 합계로 계산한 현재고, belowReorder: 재주문 기준(>0) 이하 */
	public record ItemResponse(Long id, String itemCode, String itemName, String unitName, BigDecimal reorderLevel,
			boolean active, BigDecimal currentStock, boolean belowReorder) {

		static ItemResponse of(InventoryItem i, BigDecimal stock) {
			BigDecimal s = stock == null ? BigDecimal.ZERO : stock;
			return new ItemResponse(i.getId(), i.getItemCode(), i.getItemName(), i.getUnitName(), i.getReorderLevel(),
					i.isActive(), s, i.needsReorder(s));
		}
	}

	// ---- 재고 수불 ----

	/**
	 * direction 은 type=ADJUSTMENT 일 때만 필수(INCREASE/DECREASE), 다른 유형에는 넣지 않는다.
	 * occurredAt 을 비우면 지금. unitCost 를 비우면 0.
	 */
	public record TransactionRequest(@NotNull Long inventoryItemId, Long facilityId,
			@NotNull InventoryTransactionType type, AdjustmentDirection direction,
			@NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitCost,
			@Size(max = 60) String referenceNo, LocalDateTime occurredAt, @Size(max = 500) String memo) {
	}

	/** signedQuantity: 현재고에 반영된 부호 있는 수량, adjustment: 실사 조정 거래 여부 */
	public record TransactionResponse(Long id, Long inventoryItemId, String itemCode, String itemName,
			Long facilityId, InventoryTransactionType type, BigDecimal quantity, BigDecimal signedQuantity,
			boolean adjustment, BigDecimal unitCost, String referenceNo, LocalDateTime occurredAt,
			Long actorAdminUserId, String memo) {

		static TransactionResponse of(InventoryTransaction t, InventoryItem i) {
			return new TransactionResponse(t.getId(), t.getInventoryItemId(), i == null ? null : i.getItemCode(),
					i == null ? null : i.getItemName(), t.getFacilityId(), t.getType(), t.getQuantity(),
					t.signedQuantity(), t.isAdjustment(), t.getUnitCost(), t.getReferenceNo(), t.getOccurredAt(),
					t.getActorAdminUserId(), t.getMemo());
		}
	}

	// ---- 발주 ----

	public record PoLineRequest(@NotNull Long inventoryItemId,
			@NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal orderedQuantity,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
	}

	public record PoRequest(@NotBlank @Size(max = 150) String vendorName, LocalDate expectedAt,
			@Size(max = 500) String memo, @NotEmpty @Size(max = 200) List<@Valid @NotNull PoLineRequest> items) {
	}

	public record ReceiveLine(@NotNull Long purchaseOrderItemId,
			@NotNull @Positive @Digits(integer = 9, fraction = 3) BigDecimal quantity) {
	}

	/** facilityId: 입고 장소(선택). 각 줄마다 RECEIPT 수불이 발주 단가로 생긴다. */
	public record ReceiveRequest(@NotEmpty @Size(max = 200) List<@Valid @NotNull ReceiveLine> lines, Long facilityId,
			@Size(max = 500) String memo) {
	}

	public record PoItemResponse(Long id, Long inventoryItemId, String itemCode, String itemName, String unitName,
			BigDecimal orderedQuantity, BigDecimal receivedQuantity, BigDecimal remainingQuantity,
			BigDecimal unitCost, BigDecimal amount) {

		static PoItemResponse of(PurchaseOrderItem p, InventoryItem i) {
			return new PoItemResponse(p.getId(), p.getInventoryItemId(), i == null ? null : i.getItemCode(),
					i == null ? null : i.getItemName(), i == null ? null : i.getUnitName(), p.getOrderedQuantity(),
					p.getReceivedQuantity(), p.remainingQuantity(), p.getUnitCost(), p.amount());
		}
	}

	public record PoSummary(Long id, String poNo, String vendorName, LocalDate orderedAt, LocalDate expectedAt,
			PurchaseOrderStatus status, BigDecimal totalAmount, Long createdBy) {

		static PoSummary of(PurchaseOrder p) {
			return new PoSummary(p.getId(), p.getPoNo(), p.getVendorName(), p.getOrderedAt(), p.getExpectedAt(),
					p.getStatus(), p.getTotalAmount(), p.getCreatedBy());
		}
	}

	public record PoResponse(Long id, String poNo, String vendorName, LocalDate orderedAt, LocalDate expectedAt,
			PurchaseOrderStatus status, BigDecimal totalAmount, Long createdBy, String memo,
			List<PoItemResponse> items) {

		static PoResponse of(PurchaseOrder p, Map<Long, InventoryItem> items) {
			return new PoResponse(p.getId(), p.getPoNo(), p.getVendorName(), p.getOrderedAt(), p.getExpectedAt(),
					p.getStatus(), p.getTotalAmount(), p.getCreatedBy(), p.getMemo(), p.getItems().stream()
							.map(x -> PoItemResponse.of(x, items.get(x.getInventoryItemId()))).toList());
		}
	}
}
