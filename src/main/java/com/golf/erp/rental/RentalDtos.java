package com.golf.erp.rental;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 장비 렌탈(품목 마스터·재고·렌탈·결제) API 요청/응답 */
public final class RentalDtos {
	private RentalDtos() {
	}

	// ------------------------------------------------------------------ 품목 마스터

	/** rentable 생략 시 true */
	public record RentalEquipmentCreateRequest(@NotBlank @Size(max = 40) String code,
			@NotBlank @Size(max = 150) String name, @NotNull EquipmentCategory category,
			@Size(max = 100) String brand, @Size(max = 100) String modelName, @Size(max = 30) String sizeLabel,
			@Size(max = 1000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRentalFee,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
			Boolean rentable) {
	}

	/** 장비 코드는 바꿀 수 없다. */
	public record RentalEquipmentUpdateRequest(@NotBlank @Size(max = 150) String name,
			@NotNull EquipmentCategory category, @Size(max = 100) String brand, @Size(max = 100) String modelName,
			@Size(max = 30) String sizeLabel, @Size(max = 1000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRentalFee,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
			@NotNull Boolean rentable) {
	}

	/** active=false 면 rentable 도 false 로 저장된다. */
	public record RentalEquipmentFlagsRequest(@NotNull Boolean active, @NotNull Boolean rentable) {
	}

	public record RentalEquipmentResponse(Long id, String code, String name, EquipmentCategory category, String brand,
			String modelName, String sizeLabel, String description, BigDecimal dailyRentalFee,
			BigDecimal depositAmount, boolean rentable, boolean active) {

		static RentalEquipmentResponse of(RentalEquipment e) {
			return new RentalEquipmentResponse(e.getId(), e.getCode(), e.getName(), e.getCategory(), e.getBrand(),
					e.getModelName(), e.getSizeLabel(), e.getDescription(), e.getDailyRentalFee(),
					e.getDepositAmount(), e.isRentable(), e.isActive());
		}
	}

	// ------------------------------------------------------------------ 재고

	/** stockId 가 null 이면 이 골프장에 아직 입고된 적 없는 품목(수량 모두 0) */
	public record StockResponse(Long stockId, Long rentalEquipmentId, String equipmentCode, String equipmentName,
			EquipmentCategory category, String sizeLabel, boolean active, boolean rentable, Long golfCourseId,
			int totalQuantity, int availableQuantity, int rentedQuantity, int maintenanceQuantity,
			LocalDateTime updatedAt) {

		static StockResponse of(RentalEquipment e, Long golfCourseId, RentalEquipmentStock s) {
			if (s == null)
				return new StockResponse(null, e.getId(), e.getCode(), e.getName(), e.getCategory(), e.getSizeLabel(),
						e.isActive(), e.isRentable(), golfCourseId, 0, 0, 0, 0, null);
			return new StockResponse(s.getId(), e.getId(), e.getCode(), e.getName(), e.getCategory(),
					e.getSizeLabel(), e.isActive(), e.isRentable(), s.getGolfCourseId(), s.getTotalQuantity(),
					s.getAvailableQuantity(), s.getRentedQuantity(), s.getMaintenanceQuantity(), s.getUpdatedAt());
		}
	}

	/** 분실·폐기(LOSS) 시 어느 칸에서 뺄지 */
	public enum LossSource {
		AVAILABLE, MAINTENANCE
	}

	/**
	 * 재고 직접 처리.
	 * <ul>
	 * <li>STOCK_IN: quantity 만큼 총·가용 증가(재고 행이 없으면 만든다)</li>
	 * <li>MAINTENANCE_OUT: 가용 → 정비 중, MAINTENANCE_IN: 정비 중 → 가용</li>
	 * <li>LOSS: lossFrom(기본 AVAILABLE) 칸과 총 수량에서 차감</li>
	 * <li>ADJUSTMENT: 실사 결과 가용 수량을 targetAvailable 로 맞추고 차이만큼 총 수량 증감(quantity 무시)</li>
	 * </ul>
	 * RENT_OUT·RETURN 은 렌탈 출고·반납에서만 기록된다.
	 */
	public record StockTransactionRequest(@NotNull StockTxnType type, @Positive @Max(100000) Integer quantity,
			@PositiveOrZero @Max(100000) Integer targetAvailable, LossSource lossFrom,
			@Size(max = 400) String reason) {
	}

	public record StockTxnResponse(Long id, Long rentalEquipmentId, String equipmentCode, String equipmentName,
			Long golfCourseId, StockTxnType type, int quantity, String referenceType, Long referenceId, String reason,
			Long actorAdminUserId, LocalDateTime occurredAt) {

		static StockTxnResponse of(RentalEquipmentStockTxn t, RentalEquipment e) {
			return new StockTxnResponse(t.getId(), t.getRentalEquipmentId(), e == null ? null : e.getCode(),
					e == null ? null : e.getName(), t.getGolfCourseId(), t.getType(), t.getQuantity(),
					t.getReferenceType(), t.getReferenceId(), t.getReason(), t.getActorAdminUserId(),
					t.getOccurredAt());
		}
	}

	public record StockTransactionResult(StockResponse stock, StockTxnResponse transaction) {
	}

	// ------------------------------------------------------------------ 렌탈

	/** renterName·renterPhone 을 비우고 memberId 를 주면 회원 이름·전화번호를 쓴다. */
	public record RentalCreateRequest(@NotNull Long golfCourseId, Long reservationId, Long roundId, Long memberId,
			@Size(max = 100) String renterName, @Size(max = 30) String renterPhone, @Size(max = 500) String memo,
			@NotEmpty @Size(max = 50) List<@Valid @NotNull RentalItemRequest> items) {
	}

	/** dailyRentalFee·depositAmount 를 생략하면 품목 마스터 값을 복사한다. rentalDays 기본 1 */
	public record RentalItemRequest(@NotNull Long rentalEquipmentId, Long equipmentId,
			@NotNull @Positive @Max(1000) Integer quantity,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRentalFee,
			@Positive @Max(365) Integer rentalDays,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
			@Size(max = 500) String conditionOut) {
	}

	/** dueAt 생략 시 오늘 + (최대 대여 일수 - 1)일 23:59:59. items 로 품목별 개별 자산·출고 상태를 기록할 수 있다. */
	public record RentalIssueRequest(LocalDateTime dueAt, List<@Valid @NotNull IssueItemRequest> items) {
	}

	public record IssueItemRequest(@NotNull Long itemId, Long equipmentId, @Size(max = 500) String conditionOut) {
	}

	public record RentalReturnRequest(@NotEmpty @Size(max = 50) List<@Valid @NotNull ReturnItemRequest> items) {
	}

	/**
	 * 품목별 반납. returnedQuantity(정상) + damagedQuantity(파손) + lostQuantity(분실) 가 이번에 처리할 수량이다(미반납 수량 이하).
	 * damageFee 는 이번에 더할 변상금.
	 */
	public record ReturnItemRequest(@NotNull Long itemId, @PositiveOrZero Integer returnedQuantity,
			@PositiveOrZero Integer damagedQuantity, @PositiveOrZero Integer lostQuantity,
			@Size(max = 500) String conditionIn,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal damageFee) {
	}

	/** addRentalDays: 대여 중인 품목의 대여 일수에 더할 일수(대여료 재계산). 생략하면 기한만 바꾼다. */
	public record RentalExtendRequest(@NotNull LocalDateTime dueAt, @PositiveOrZero @Max(365) Integer addRentalDays,
			@Size(max = 500) String reason) {
	}

	/** null 인 항목은 그대로 둔다. damageFee 는 누적값을 이 값으로 바꾼다. */
	public record RentalItemFeeRequest(@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal dailyRentalFee,
			@Positive @Max(365) Integer rentalDays,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal damageFee,
			@Size(max = 500) String reason) {
	}

	public record ReasonRequest(@Size(max = 500) String reason) {
	}

	public record RentalPaymentRequest(@NotNull RentalPaymentType type, @NotNull RentalPaymentMethod method,
			@NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@Size(max = 100) String approvalNo) {
	}

	public record MarkOverdueResult(int count, List<Long> rentalIds) {
	}

	/** overdue: 대여 중(RENTED·PARTIALLY_RETURNED·OVERDUE)이면서 반납 기한이 지났는지 */
	public record RentalSummaryResponse(Long id, String rentalNo, Long golfCourseId, Long reservationId, Long roundId,
			Long memberId, String renterName, String renterPhone, RentalStatus status, LocalDateTime rentedAt,
			LocalDateTime dueAt, LocalDateTime returnedAt, BigDecimal rentalFeeTotal, BigDecimal depositTotal,
			BigDecimal damageFeeTotal, boolean overdue, String memo, LocalDateTime createdAt) {

		static RentalSummaryResponse of(EquipmentRental r, LocalDateTime now) {
			return new RentalSummaryResponse(r.getId(), r.getRentalNo(), r.getGolfCourseId(), r.getReservationId(),
					r.getRoundId(), r.getMemberId(), r.getRenterName(), r.getRenterPhone(), r.getStatus(),
					r.getRentedAt(), r.getDueAt(), r.getReturnedAt(), r.getRentalFeeTotal(), r.getDepositTotal(),
					r.getDamageFeeTotal(), r.isOverdue(now), r.getMemo(), r.getCreatedAt());
		}
	}

	public record RentalDetailResponse(Long id, String rentalNo, Long golfCourseId, Long reservationId, Long roundId,
			Long memberId, String renterName, String renterPhone, RentalStatus status, LocalDateTime rentedAt,
			LocalDateTime dueAt, LocalDateTime returnedAt, BigDecimal rentalFeeTotal, BigDecimal depositTotal,
			BigDecimal damageFeeTotal, boolean overdue, Long receivedBy, Long returnedTo, String memo,
			LocalDateTime createdAt, List<RentalItemResponse> items, List<RentalPaymentResponse> payments,
			List<RentalHistoryResponse> history, RentalBalance balance) {
	}

	/**
	 * outstandingQuantity: 아직 처리(반납·파손·분실)되지 않은 수량. damagedQuantity·lostQuantity 는 이력 집계.
	 * rentalFee = 일 대여료 × 대여 일수 × 수량, depositTotal = 보증금 × 수량
	 */
	public record RentalItemResponse(Long id, Long rentalEquipmentId, String equipmentCode, String equipmentName,
			Long equipmentId, int quantity, int returnedQuantity, int outstandingQuantity, int damagedQuantity,
			int lostQuantity, BigDecimal dailyRentalFee, int rentalDays, BigDecimal rentalFee,
			BigDecimal depositAmount, BigDecimal depositTotal, BigDecimal damageFee, RentalItemStatus status,
			String conditionOut, String conditionIn, LocalDateTime returnedAt) {

		static RentalItemResponse of(EquipmentRentalItem i, RentalEquipment e, int damaged, int lost) {
			return new RentalItemResponse(i.getId(), i.getRentalEquipmentId(), e == null ? null : e.getCode(),
					e == null ? null : e.getName(), i.getEquipmentId(), i.getQuantity(), i.getReturnedQuantity(),
					i.outstandingQuantity(), damaged, lost, i.getDailyRentalFee(), i.getRentalDays(), i.rentalFee(),
					i.getDepositAmount(), i.depositTotal(), i.getDamageFee(), i.getStatus(), i.getConditionOut(),
					i.getConditionIn(), i.getReturnedAt());
		}
	}

	public record RentalPaymentResponse(Long id, RentalPaymentType type, RentalPaymentMethod method,
			BigDecimal amount, RentalPaymentStatus status, String approvalNo, LocalDateTime paidAt,
			LocalDateTime cancelledAt, Long receivedBy) {

		static RentalPaymentResponse of(EquipmentRentalPayment p) {
			return new RentalPaymentResponse(p.getId(), p.getType(), p.getMethod(), p.getAmount(), p.getStatus(),
					p.getApprovalNo(), p.getPaidAt(), p.getCancelledAt(), p.getReceivedBy());
		}
	}

	public record RentalHistoryResponse(Long id, Long itemId, RentalAction action, Integer quantity,
			String conditionNote, Long actorAdminUserId, LocalDateTime occurredAt) {

		static RentalHistoryResponse of(EquipmentRentalHistory h) {
			return new RentalHistoryResponse(h.getId(), h.getEquipmentRentalItemId(), h.getAction(), h.getQuantity(),
					h.getConditionNote(), h.getActorAdminUserId(), h.getOccurredAt());
		}
	}

	/**
	 * 수납 현황(취소된 결제 제외).
	 * <ul>
	 * <li>feeDue = 대여료 + 파손료, feeUnpaid = feeDue - (받은 대여료 + 받은 파손료), 0 미만이면 0</li>
	 * <li>depositHeld = 받은 보증금 - 환불액</li>
	 * <li>netReceived = 받은 대여료 + 파손료 + 보증금 - 환불액 (지금 고객에게서 받아 둔 순액)</li>
	 * <li>settlementAmount = netReceived - feeDue. 반납 정산 시 양수면 돌려줄 금액(보증금에서 미수 요금을 뺀 나머지), 음수면 더 받을 금액</li>
	 * </ul>
	 */
	public record RentalBalance(BigDecimal rentalFeeTotal, BigDecimal rentalFeePaid, BigDecimal damageFeeTotal,
			BigDecimal damageFeePaid, BigDecimal feeDue, BigDecimal feeUnpaid, BigDecimal depositTotal,
			BigDecimal depositPaid, BigDecimal refunded, BigDecimal depositHeld, BigDecimal netReceived,
			BigDecimal settlementAmount) {

		static RentalBalance of(EquipmentRental r, List<EquipmentRentalPayment> payments) {
			Map<RentalPaymentType, BigDecimal> paid = new EnumMap<>(RentalPaymentType.class);
			for (RentalPaymentType t : RentalPaymentType.values())
				paid.put(t, BigDecimal.ZERO);
			for (EquipmentRentalPayment p : payments)
				if (p.isPaid())
					paid.merge(p.getType(), p.getAmount(), BigDecimal::add);
			BigDecimal feePaid = paid.get(RentalPaymentType.RENTAL_FEE).add(paid.get(RentalPaymentType.DAMAGE_FEE));
			BigDecimal feeDue = r.getRentalFeeTotal().add(r.getDamageFeeTotal());
			BigDecimal refunded = paid.get(RentalPaymentType.REFUND);
			BigDecimal depositPaid = paid.get(RentalPaymentType.DEPOSIT);
			BigDecimal net = feePaid.add(depositPaid).subtract(refunded);
			return new RentalBalance(r.getRentalFeeTotal(), paid.get(RentalPaymentType.RENTAL_FEE),
					r.getDamageFeeTotal(), paid.get(RentalPaymentType.DAMAGE_FEE), feeDue,
					feeDue.subtract(feePaid).max(BigDecimal.ZERO), r.getDepositTotal(), depositPaid, refunded,
					depositPaid.subtract(refunded), net, net.subtract(feeDue));
		}
	}
}
