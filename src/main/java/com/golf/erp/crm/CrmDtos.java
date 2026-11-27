package com.golf.erp.crm;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 고객 알림·프로모션 API 요청/응답 (고객 문의는 board 모듈의 INQUIRY 게시글) */
public final class CrmDtos {
	private CrmDtos() {
	}

	public record ActiveRequest(@NotNull Boolean active) {
	}

	// ---- 알림 ----

	/** scheduledAt 을 비우면 즉시 발송 대상 */
	public record NotificationCreateRequest(Long memberId, Long reservationId, @NotNull NotificationChannel channel,
			@Size(max = 60) String templateCode, @NotBlank @Size(max = 255) String destination,
			@NotBlank @Size(max = 4000) String message, LocalDateTime scheduledAt) {
	}

	public record MarkSentRequest(@Size(max = 150) String providerMessageId) {
	}

	public record MarkFailedRequest(@NotBlank @Size(max = 1000) String errorMessage,
			@Size(max = 150) String providerMessageId) {
	}

	/** scheduledAt 을 비우면 즉시 재발송 대상 */
	public record RetryRequest(LocalDateTime scheduledAt) {
	}

	public record NotificationResponse(Long id, Long memberId, Long reservationId, NotificationChannel channel,
			String templateCode, String destination, String message, NotificationStatus status,
			LocalDateTime scheduledAt, LocalDateTime sentAt, String providerMessageId, String errorMessage) {

		static NotificationResponse of(CustomerNotification n) {
			return new NotificationResponse(n.getId(), n.getMemberId(), n.getReservationId(), n.getChannel(),
					n.getTemplateCode(), n.getDestination(), n.getMessage(), n.getStatus(), n.getScheduledAt(),
					n.getSentAt(), n.getProviderMessageId(), n.getErrorMessage());
		}
	}

	// ---- 프로모션 ----

	/** code 는 대문자로 저장. PERCENT 는 discountValue ≤ 100. 기간은 [startsAt, endsAt) */
	public record PromotionRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 150) String name,
			@NotNull DiscountType discountType,
			@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal discountValue,
			@NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {
	}

	/** current: 지금 사용 가능(활성 + 기간 안) 여부 */
	public record PromotionResponse(Long id, String code, String name, DiscountType discountType,
			BigDecimal discountValue, LocalDateTime startsAt, LocalDateTime endsAt, boolean active, boolean current) {

		static PromotionResponse of(Promotion p, LocalDateTime now) {
			return new PromotionResponse(p.getId(), p.getCode(), p.getName(), p.getDiscountType(),
					p.getDiscountValue(), p.getStartsAt(), p.getEndsAt(), p.isActive(), p.unavailableReason(now) == null);
		}
	}

	/** 코드 검증 결과. valid=false 면 message 에 사유, discountAmount 는 0 */
	public record PromotionQuote(boolean valid, String message, Long promotionId, String code, String name,
			DiscountType discountType, BigDecimal discountValue, BigDecimal baseAmount, BigDecimal discountAmount,
			BigDecimal finalAmount) {
	}

	/** baseAmount: 할인 기준 금액(예: 그린피 합계). 할인액은 서버가 계산한다 */
	public record RedemptionRequest(@NotNull Long memberId, Long reservationId,
			@NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal baseAmount) {
	}

	public record RedemptionResponse(Long id, Long promotionId, String promotionCode, Long memberId,
			Long reservationId, LocalDateTime redeemedAt, BigDecimal discountAmount) {

		static RedemptionResponse of(PromotionRedemption r, Promotion p) {
			return new RedemptionResponse(r.getId(), r.getPromotionId(), p == null ? null : p.getCode(),
					r.getMemberId(), r.getReservationId(), r.getRedeemedAt(), r.getDiscountAmount());
		}
	}
}
