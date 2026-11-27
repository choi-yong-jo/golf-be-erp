package com.golf.erp.reservation;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * golf.reservation. 예약 앱과 같은 테이블이라 컬럼 의미를 맞춘다.
 * <ul>
 * <li>greenFeeAmount = 1인 그린피, discountAmount = 1인 할인액, totalAmount = (그린피 - 할인) x 인원</li>
 * <li>reserved_at = 예약한 시각, play_at = 플레이 일시(티타임 일시를 복사해 둔다)</li>
 * <li>version 은 예약 앱과 공유하는 낙관적 락이다.</li>
 * </ul>
 */
@Entity
@Table(name = "reservation")
public class Reservation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_id")
	private Long id;
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
	private ReservationStatus status = ReservationStatus.PENDING;
	@Column(name = "player_count", nullable = false)
	private Integer playerCount;
	@Column(name = "green_fee_amount", precision = 12, scale = 2)
	private BigDecimal greenFeeAmount;
	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;
	@Column(name = "total_amount", precision = 12, scale = 2)
	private BigDecimal totalAmount;
	@Column(name = "memo", length = 500)
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
	@Column(name = "version", nullable = false)
	private Long version;

	protected Reservation() {
	}

	/** ERP 예약은 PENDING 으로 시작한다(PENDING_PAYMENT 는 예약 앱이 10분 뒤 자동 취소하는 상태라 쓰지 않는다). */
	public Reservation(Long memberId, Long teeTimeId, Long golfCourseId, LocalDateTime playAt, int playerCount,
			BigDecimal greenFeePerPlayer, BigDecimal discountPerPlayer, String memo, LocalDateTime now) {
		if (discountPerPlayer.compareTo(greenFeePerPlayer) > 0)
			throw new IllegalArgumentException("1인 할인액은 그린피보다 클 수 없습니다.");
		this.memberId = memberId;
		this.teeTimeId = teeTimeId;
		this.golfCourseId = golfCourseId;
		this.playAt = playAt;
		this.playerCount = playerCount;
		this.greenFeeAmount = greenFeePerPlayer.setScale(2, RoundingMode.HALF_UP);
		this.discountAmount = discountPerPlayer.setScale(2, RoundingMode.HALF_UP);
		this.totalAmount = greenFeeAmount.subtract(discountAmount).multiply(BigDecimal.valueOf(playerCount))
				.setScale(2, RoundingMode.HALF_UP);
		this.memo = memo;
		this.status = ReservationStatus.PENDING;
		this.reservedAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 저장(flush) 후 발급한 예약번호를 넣는다. */
	public void assignReservationNo(String reservationNo) {
		this.reservationNo = reservationNo;
	}

	/** PENDING → CONFIRMED (결제 없이 확정, 예: 현장 후불) */
	public void confirm(LocalDateTime now) {
		if (status != ReservationStatus.PENDING)
			throw new ConflictException("대기(PENDING) 상태의 예약만 확정할 수 있습니다. (현재: " + status + ")");
		markConfirmed(now);
	}

	/** 현장 결제 가능 여부. PENDING·CONFIRMED 만 결제할 수 있다. */
	public void requirePayable() {
		if (status != ReservationStatus.PENDING && status != ReservationStatus.CONFIRMED)
			throw new ConflictException("결제할 수 없는 예약 상태입니다. (현재: " + status + ")");
		if (totalAmount == null || totalAmount.signum() <= 0)
			throw new ConflictException("결제할 금액이 없습니다. 0원 예약은 확정만 하면 됩니다.");
	}

	/** 결제 완료. PENDING 이면 함께 확정한다. */
	public void paid(LocalDateTime now) {
		requirePayable();
		if (status == ReservationStatus.PENDING)
			markConfirmed(now);
		else
			this.updatedAt = now;
	}

	/** 취소 가능 여부. 예약 앱이 결제·환불 중이면(PAYING, REFUND_PENDING) 막는다. */
	public void requireCancellable() {
		if (ReservationStatus.IN_PROGRESS_BY_APP.contains(status))
			throw new ConflictException("예약 앱에서 결제 또는 환불을 처리 중인 예약입니다. 잠시 후 다시 확인하세요.");
		if (status != ReservationStatus.PENDING && status != ReservationStatus.PENDING_PAYMENT
				&& status != ReservationStatus.CONFIRMED)
			throw new ConflictException("취소할 수 없는 예약 상태입니다. (현재: " + status + ")");
	}

	public void cancel(String reason, LocalDateTime now) {
		requireCancellable();
		this.status = ReservationStatus.CANCELLED;
		this.cancelReason = reason;
		this.cancelledAt = now;
		this.updatedAt = now;
	}

	/** CONFIRMED → COMPLETED. 플레이 시작 시각 이후에만 가능 */
	public void complete(LocalDateTime effectivePlayAt, LocalDateTime now) {
		requireFinishable(effectivePlayAt, now);
		this.status = ReservationStatus.COMPLETED;
		this.updatedAt = now;
	}

	/** CONFIRMED → NO_SHOW. 플레이 시작 시각 이후에만 가능 */
	public void noShow(LocalDateTime effectivePlayAt, LocalDateTime now) {
		requireFinishable(effectivePlayAt, now);
		this.status = ReservationStatus.NO_SHOW;
		this.updatedAt = now;
	}

	/**
	 * 라운드 종료에 따른 완료. 이미 종료 상태면 false(변경 없음). 예약 앱이 결제·환불 중이면 409.
	 */
	public boolean completeByRound(LocalDateTime now) {
		if (ReservationStatus.FINISHED.contains(status))
			return false;
		if (ReservationStatus.IN_PROGRESS_BY_APP.contains(status))
			throw new ConflictException("예약 앱에서 결제 또는 환불을 처리 중인 예약이라 완료 처리할 수 없습니다. (예약 "
					+ (reservationNo == null ? id : reservationNo) + ")");
		this.status = ReservationStatus.COMPLETED;
		this.updatedAt = now;
		return true;
	}

	/** 라운드 체크인 가능 여부(PENDING·CONFIRMED 만) */
	public void requireCheckInable() {
		if (!ReservationStatus.CHECK_IN_ALLOWED.contains(status)) {
			String why = switch (status) {
			case PENDING_PAYMENT, PAYING -> "예약 앱에서 결제가 끝나지 않은 예약입니다.";
			case REFUND_PENDING -> "예약 앱에서 환불 중인 예약입니다.";
			case CANCELLED, FAILED -> "취소되었거나 실패한 예약입니다.";
			case COMPLETED -> "이미 완료된 예약입니다.";
			case NO_SHOW -> "노쇼 처리된 예약입니다.";
			default -> "체크인할 수 없는 예약입니다.";
			};
			throw new ConflictException(why + " (현재: " + status + ")");
		}
	}

	public void changeMemo(String memo, LocalDateTime now) {
		this.memo = memo;
		this.updatedAt = now;
	}

	private void requireFinishable(LocalDateTime effectivePlayAt, LocalDateTime now) {
		if (status != ReservationStatus.CONFIRMED)
			throw new ConflictException("확정(CONFIRMED)된 예약만 처리할 수 있습니다. (현재: " + status + ")");
		if (effectivePlayAt != null && effectivePlayAt.isAfter(now))
			throw new ConflictException("플레이 시작 시각 이후에 처리할 수 있습니다.");
	}

	private void markConfirmed(LocalDateTime now) {
		this.status = ReservationStatus.CONFIRMED;
		this.confirmedAt = now;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
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

	public ReservationStatus getStatus() {
		return status;
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
