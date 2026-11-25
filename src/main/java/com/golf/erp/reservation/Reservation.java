package com.golf.erp.reservation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.reservation. 금액 규칙(예약 앱과 동일한 컬럼 의미):
 * greenFeeAmount = 1인 그린피, discountAmount = 1인 할인액, totalAmount = (그린피 - 할인) x 인원.
 * version 은 예약 앱과 공유하는 낙관적 락이다.
 */
@Entity
@Table(name = "reservation")
public class Reservation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_id")
	private Long reservationId;
	@Column(name = "reservation_no", length = 30)
	private String reservationNo;
	@Column(name = "member_id", nullable = false)
	private Long memberId;
	@Column(name = "tee_time_id")
	private Long teeTimeId;
	@Column(name = "golf_course_id")
	private Long golfCourseId;
	@Column(name = "play_at")
	private LocalDateTime playAt;
	@Enumerated(EnumType.STRING)
	@Column(name = "reservation_status", nullable = false, length = 20)
	private ReservationStatus reservationStatus;
	@Column(name = "player_count", nullable = false)
	private Integer playerCount;
	@Column(name = "green_fee_amount", precision = 12, scale = 2)
	private BigDecimal greenFeeAmount;
	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount;
	@Column(name = "total_amount", precision = 12, scale = 2)
	private BigDecimal totalAmount;
	@Column(length = 500)
	private String memo;
	@Column(name = "reserved_at", nullable = false, updatable = false)
	private LocalDateTime reservedAt;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	@Column(name = "payment_started_at")
	private LocalDateTime paymentStartedAt;
	@Column(name = "confirmed_at")
	private LocalDateTime confirmedAt;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;
	@Version
	@Column(nullable = false)
	private Long version;

	protected Reservation() {
	}

	/** ERP 예약: PENDING 으로 시작한다 (PENDING_PAYMENT 는 예약 앱의 10분 만료 대상) */
	public Reservation(Long memberId, Long teeTimeId, Long golfCourseId, LocalDateTime playAt, int playerCount,
			BigDecimal greenFeeAmount, BigDecimal discountAmount, String memo, LocalDateTime now) {
		this.memberId = memberId;
		this.teeTimeId = teeTimeId;
		this.golfCourseId = golfCourseId;
		this.playAt = playAt;
		this.playerCount = playerCount;
		this.greenFeeAmount = greenFeeAmount;
		this.discountAmount = discountAmount;
		this.totalAmount = greenFeeAmount.subtract(discountAmount).multiply(BigDecimal.valueOf(playerCount));
		this.memo = memo;
		this.reservationStatus = ReservationStatus.PENDING;
		this.reservedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 예약 앱의 GOLF-R{id} 와 겹치지 않도록 ERP-R{id} */
	public void assignReservationNo() {
		this.reservationNo = "ERP-R" + reservationId;
	}

	public void confirm(LocalDateTime now) {
		this.reservationStatus = ReservationStatus.CONFIRMED;
		this.confirmedAt = now;
		this.updatedAt = now;
	}

	public void cancel(String reason, LocalDateTime now) {
		this.reservationStatus = ReservationStatus.CANCELLED;
		this.cancelReason = reason;
		this.cancelledAt = now;
		this.updatedAt = now;
	}

	public void finish(ReservationStatus status, LocalDateTime now) {
		this.reservationStatus = status;
		this.updatedAt = now;
	}

	public void changeMemo(String memo, LocalDateTime now) {
		this.memo = memo;
		this.updatedAt = now;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public String getReservationNo() {
		return reservationNo;
	}

	public Long getMemberId() {
		return memberId;
	}

	public Long getTeeTimeId() {
		return teeTimeId;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public LocalDateTime getPlayAt() {
		return playAt;
	}

	public ReservationStatus getReservationStatus() {
		return reservationStatus;
	}

	public Integer getPlayerCount() {
		return playerCount;
	}

	public BigDecimal getGreenFeeAmount() {
		return greenFeeAmount;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getReservedAt() {
		return reservedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDateTime getPaymentStartedAt() {
		return paymentStartedAt;
	}

	public LocalDateTime getConfirmedAt() {
		return confirmedAt;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public Long getVersion() {
		return version;
	}
}
