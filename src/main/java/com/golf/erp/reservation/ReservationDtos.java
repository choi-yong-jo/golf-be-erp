package com.golf.erp.reservation;

import com.golf.erp.reservation.ReservationSql.PaymentRow;
import com.golf.erp.reservation.ReservationSql.TeeTimeRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 예약 API 요청/응답 */
public final class ReservationDtos {
	private ReservationDtos() {
	}

	/**
	 * ERP 예약 등록. companions 는 예약자 본인을 뺀 동반자(최대 playerCount-1명)이며, 예약자는 1번 플레이어로 자동 등록된다.
	 */
	public record ReservationCreateRequest(@NotNull Long teeTimeId, @NotNull Long memberId,
			@NotNull @Min(1) @Max(8) Integer playerCount, @Size(max = 500) String memo,
			@Valid @Size(max = 7) List<PlayerRequest> companions) {
	}

	public record PlayerRequest(@NotBlank @Size(max = 100) String name, @Size(max = 30) String phone, Long memberId) {
	}

	/** 현장 결제. 금액은 예약 총액(totalAmount)으로 고정된다. */
	public record PaymentRequest(
			@NotBlank @Pattern(regexp = "CARD|CASH|BANK_TRANSFER|KAKAO_PAY|NAVER_PAY", message = "결제 수단은 CARD, CASH, BANK_TRANSFER, KAKAO_PAY, NAVER_PAY 중 하나입니다.") String paymentMethod) {
	}

	/** reason 생략 시 "관리자 취소" */
	public record CancelRequest(@Size(max = 500) String reason) {
	}

	public record MemoRequest(@Size(max = 500) String memo) {
	}

	/** 예약 목록 한 줄(예약 앱이 만든 예약 포함). playAt 은 play_at 이 없으면 티타임 일시 */
	public record ReservationRow(Long id, String reservationNo, Long memberId, String memberName, String memberPhone,
			Long golfCourseId, String golfCourseName, Long teeTimeId, String course, LocalDateTime playAt,
			int playerCount, ReservationStatus status, BigDecimal totalAmount, LocalDateTime reservedAt) {
	}

	public record PlayerResponse(Long id, Short order, String name, String phone, Long memberId,
			boolean bookingMember) {

		static PlayerResponse of(ReservationPlayer p) {
			return new PlayerResponse(p.getId(), p.getPlayerOrder(), p.getPlayerName(), p.getPhone(), p.getMemberId(),
					p.isBookingMember());
		}
	}

	public record HistoryResponse(Long id, String actionType, String beforeStatus, String afterStatus,
			Long beforeTeeTimeId, Long afterTeeTimeId, BigDecimal beforeAmount, BigDecimal afterAmount, String reason,
			Long actorMemberId, LocalDateTime createdAt) {

		static HistoryResponse of(ReservationHistory h) {
			return new HistoryResponse(h.getId(), h.getActionType(), h.getBeforeStatus(), h.getAfterStatus(),
					h.getBeforeTeeTimeId(), h.getAfterTeeTimeId(), h.getBeforeAmount(), h.getAfterAmount(),
					h.getReason(), h.getActorMemberId(), h.getCreatedAt());
		}
	}

	/** erp: ERP 현장 결제(pg_provider=ERP) 여부. 그 밖의 결제는 예약 앱 온라인(PG) 결제다. */
	public record PaymentResponse(Long id, String paymentType, String status, String method, String pgProvider,
			String pgTransactionId, boolean erp, BigDecimal amount, LocalDateTime requestedAt,
			LocalDateTime completedAt, LocalDateTime refundedAt, String failureReason) {

		static PaymentResponse of(PaymentRow p) {
			return new PaymentResponse(p.id(), p.type(), p.status(), p.method(), p.pgProvider(), p.pgTransactionId(),
					p.erp(), p.amount(), p.requestedAt(), p.completedAt(), p.refundedAt(), p.failureReason());
		}
	}

	public record TeeTimeSummary(Long id, LocalDate playDate, LocalTime startTime, String course, String holeType,
			int capacity, BigDecimal greenFee, String status) {

		static TeeTimeSummary of(TeeTimeRow t) {
			return t == null ? null
					: new TeeTimeSummary(t.id(), t.playDate(), t.startTime(), t.course(), t.holeType(), t.capacity(),
							t.greenFee(), t.status());
		}
	}

	/**
	 * 예약 상세. greenFeeAmount·discountAmount 는 1인 기준, totalAmount = (그린피 - 할인) x 인원. paidAmount: 결제 완료 합계(환불
	 * 제외). roundId: 이 예약으로 체크인한 라운드(없으면 null)
	 */
	public record ReservationDetail(Long id, String reservationNo, ReservationStatus status, Long memberId,
			String memberName, String memberPhone, Long golfCourseId, String golfCourseName, Long teeTimeId,
			TeeTimeSummary teeTime, LocalDateTime playAt, int playerCount, BigDecimal greenFeeAmount,
			BigDecimal discountAmount, BigDecimal totalAmount, BigDecimal paidAmount, String memo,
			LocalDateTime reservedAt, LocalDateTime confirmedAt, LocalDateTime cancelledAt, String cancelReason,
			LocalDateTime paymentStartedAt, LocalDateTime updatedAt, Long version, Long roundId,
			List<PlayerResponse> players, List<PaymentResponse> payments, List<HistoryResponse> histories) {
	}
}
