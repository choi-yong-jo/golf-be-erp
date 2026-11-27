package com.golf.erp.billing;

import com.golf.erp.round.RoundSettlementStatus;
import com.golf.erp.round.RoundStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 부대 요금표·라운드 요금/수납·일 마감 API 요청/응답 */
public final class BillingDtos {
	private BillingDtos() {
	}

	// ── 부대 요금표 ──

	/**
	 * chargeUnit 기본 TEAM, validFrom 기본 오늘, validTo 기본 9999-12-31, active 기본 true.
	 * holeBasis: 9·18·27·36 또는 null(홀 수와 무관)
	 */
	public record ServiceFeeCreateRequest(@NotNull Long golfCourseId, @NotNull FeeType feeType,
			@NotBlank @Size(max = 100) String name, ChargeUnit chargeUnit, Short holeBasis,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount, LocalDate validFrom,
			LocalDate validTo, Boolean active) {
	}

	public record ServiceFeeUpdateRequest(@NotNull FeeType feeType, @NotBlank @Size(max = 100) String name,
			@NotNull ChargeUnit chargeUnit, Short holeBasis,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@NotNull LocalDate validFrom, @NotNull LocalDate validTo, @NotNull Boolean active) {
	}

	public record ServiceFeeResponse(Long id, Long golfCourseId, FeeType feeType, String name, ChargeUnit chargeUnit,
			Short holeBasis, BigDecimal amount, LocalDate validFrom, LocalDate validTo, boolean active,
			LocalDateTime createdAt, LocalDateTime updatedAt) {

		static ServiceFeeResponse of(ServiceFee f) {
			return new ServiceFeeResponse(f.getId(), f.getGolfCourseId(), f.getFeeType(), f.getName(),
					f.getChargeUnit(), f.getHoleBasis(), f.getAmount(), f.getValidFrom(), f.getValidTo(), f.isActive(),
					f.getCreatedAt(), f.getUpdatedAt());
		}
	}

	// ── 라운드 요금 ──

	/**
	 * serviceFeeId 를 주면 부대 요금표에서 종류·단가·이름을 가져온다(chargeType·unitPrice 는 무시).
	 * 1인당(PLAYER) 요금인데 roundPlayerId 가 없으면 참가자마다 한 건씩 부과한다.
	 * serviceFeeId 가 없으면 chargeType·unitPrice 가 필요하다(직접 입력). quantity 기본 1, discountAmount 기본 0.
	 */
	public record ChargeCreateRequest(Long roundPlayerId, Long serviceFeeId, ChargeType chargeType,
			@Size(max = 200) String description,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal unitPrice, @Min(1) @Max(100) Short quantity,
			@PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal discountAmount) {
	}

	public record ReasonRequest(@NotBlank @Size(max = 200) String reason) {
	}

	/** playerName: 팀 공통 요금이면 null */
	public record ChargeResponse(Long id, Long roundId, Long roundPlayerId, String playerName, ChargeType chargeType,
			String description, BigDecimal unitPrice, Short quantity, BigDecimal discountAmount, BigDecimal amount,
			boolean voided, LocalDateTime voidedAt, String voidReason, Long createdBy, LocalDateTime createdAt) {

		static ChargeResponse of(RoundCharge c, Map<Long, String> playerNames) {
			return new ChargeResponse(c.getId(), c.getRoundId(), c.getRoundPlayerId(),
					c.getRoundPlayerId() == null ? null : playerNames.get(c.getRoundPlayerId()), c.getChargeType(),
					c.getDescription(), c.getUnitPrice(), c.getQuantity(), c.getDiscountAmount(), c.getAmount(),
					c.isVoided(), c.getVoidedAt(), c.getVoidReason(), c.getCreatedBy(), c.getCreatedAt());
		}
	}

	// ── 라운드 수납 ──

	public record PaymentCreateRequest(Long roundPlayerId, @NotNull PaymentMethod method,
			@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
			@Size(max = 100) String approvalNo, @Size(max = 200) String memo) {
	}

	public record PaymentResponse(Long id, Long roundId, Long roundPlayerId, String playerName, PaymentMethod method,
			BigDecimal amount, PaymentStatus status, String approvalNo, String memo, LocalDateTime paidAt,
			LocalDateTime cancelledAt, String cancelReason, Long receivedBy) {

		static PaymentResponse of(RoundPayment p, Map<Long, String> playerNames) {
			return new PaymentResponse(p.getId(), p.getRoundId(), p.getRoundPlayerId(),
					p.getRoundPlayerId() == null ? null : playerNames.get(p.getRoundPlayerId()), p.getMethod(),
					p.getAmount(), p.getStatus(), p.getApprovalNo(), p.getMemo(), p.getPaidAt(), p.getCancelledAt(),
					p.getCancelReason(), p.getReceivedBy());
		}
	}

	// ── 라운드 계산서 ──

	/** 라운드 요금·수납 합계(취소 제외). balance = chargeTotal - paidTotal (음수면 과수납) */
	public record BillSummary(BigDecimal chargeTotal, BigDecimal discountTotal, BigDecimal paidTotal,
			BigDecimal balance) {
	}

	/** 참가자별(또는 팀 공통: roundPlayerId·playerName null) 요금·수납 합계 */
	public record BillLine(Long roundPlayerId, String playerName, BigDecimal chargeTotal, BigDecimal paidTotal,
			BigDecimal balance) {
	}

	public record BillResponse(Long roundId, String roundNo, RoundStatus roundStatus,
			RoundSettlementStatus settlementStatus, boolean settlementClosed, BillSummary summary,
			List<BillLine> lines, List<ChargeResponse> charges, List<PaymentResponse> payments) {
	}

	// ── 일 마감 ──

	public record SettlementCalculateRequest(@NotNull Long golfCourseId, @NotNull LocalDate businessDate,
			@Size(max = 500) String memo) {
	}

	public record SettlementCloseRequest(@Size(max = 500) String memo) {
	}

	public record SettlementReopenRequest(@NotBlank @Size(max = 500) String reason) {
	}

	public record SettlementItemResponse(SettlementItemType itemType, String itemCode, int itemCount,
			BigDecimal amount) {

		static SettlementItemResponse of(DailySettlementItem i) {
			return new SettlementItemResponse(i.getItemType(), i.getItemCode(), i.getItemCount(), i.getAmount());
		}
	}

	/** 목록용(항목 제외) */
	public record SettlementSummary(Long id, Long golfCourseId, LocalDate businessDate, DailySettlementStatus status,
			int roundCount, int playerCount, BigDecimal chargeTotal, BigDecimal discountTotal, BigDecimal paymentTotal,
			BigDecimal unpaidTotal, LocalDateTime closedAt, Long closedBy, String memo, LocalDateTime updatedAt) {

		static SettlementSummary of(DailySettlement s) {
			return new SettlementSummary(s.getId(), s.getGolfCourseId(), s.getBusinessDate(), s.getStatus(),
					s.getRoundCount(), s.getPlayerCount(), s.getChargeTotal(), s.getDiscountTotal(),
					s.getPaymentTotal(), s.getUnpaidTotal(), s.getClosedAt(), s.getClosedBy(), s.getMemo(),
					s.getUpdatedAt());
		}
	}

	/**
	 * 상세. 저장된 정산이면 id 가 있고, 미리보기(preview)면 id 가 null 이다.
	 * activeRoundCount: 아직 체크인·플레이 중인 라운드 수(0 이어야 마감 가능)
	 */
	public record SettlementResponse(Long id, Long golfCourseId, LocalDate businessDate, DailySettlementStatus status,
			int roundCount, int playerCount, BigDecimal chargeTotal, BigDecimal discountTotal, BigDecimal paymentTotal,
			BigDecimal unpaidTotal, LocalDateTime closedAt, Long closedBy, String memo, Integer activeRoundCount,
			List<SettlementItemResponse> chargeItems, List<SettlementItemResponse> paymentItems,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
	}
}
