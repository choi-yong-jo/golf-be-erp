package com.golf.erp.reservation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.reservation_history. ERP가 바꾼 예약마다 한 줄씩 남긴다.
 * actionType: CREATE, CONFIRM, PAYMENT, CANCEL, COMPLETE, NO_SHOW, MEMO.
 * actorMemberId 는 회원이 한 행동일 때만 채운다(ERP 관리자 행동은 null).
 */
@Entity
@Table(name = "reservation_history")
public class ReservationHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_history_id")
	private Long reservationHistoryId;
	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;
	@Column(name = "action_type", nullable = false, length = 30)
	private String actionType;
	@Column(name = "before_status", length = 20)
	private String beforeStatus;
	@Column(name = "after_status", length = 20)
	private String afterStatus;
	@Column(name = "before_tee_time_id")
	private Long beforeTeeTimeId;
	@Column(name = "after_tee_time_id")
	private Long afterTeeTimeId;
	@Column(name = "before_amount", precision = 12, scale = 2)
	private BigDecimal beforeAmount;
	@Column(name = "after_amount", precision = 12, scale = 2)
	private BigDecimal afterAmount;
	@Column(length = 1000)
	private String reason;
	@Column(name = "actor_member_id")
	private Long actorMemberId;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReservationHistory() {
	}

	/** before: 변경 전 상태/티타임/금액 (생성이면 null) */
	public static ReservationHistory of(String actionType, Reservation after, ReservationStatus beforeStatus,
			Long beforeTeeTimeId, BigDecimal beforeAmount, String reason, LocalDateTime now) {
		ReservationHistory h = new ReservationHistory();
		h.reservationId = after.getReservationId();
		h.actionType = actionType;
		h.beforeStatus = beforeStatus == null ? null : beforeStatus.name();
		h.afterStatus = after.getReservationStatus().name();
		h.beforeTeeTimeId = beforeTeeTimeId;
		h.afterTeeTimeId = after.getTeeTimeId();
		h.beforeAmount = beforeAmount;
		h.afterAmount = after.getTotalAmount();
		h.reason = reason;
		h.createdAt = now;
		return h;
	}

	public Long getReservationHistoryId() {
		return reservationHistoryId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public String getActionType() {
		return actionType;
	}

	public String getBeforeStatus() {
		return beforeStatus;
	}

	public String getAfterStatus() {
		return afterStatus;
	}

	public Long getBeforeTeeTimeId() {
		return beforeTeeTimeId;
	}

	public Long getAfterTeeTimeId() {
		return afterTeeTimeId;
	}

	public BigDecimal getBeforeAmount() {
		return beforeAmount;
	}

	public BigDecimal getAfterAmount() {
		return afterAmount;
	}

	public String getReason() {
		return reason;
	}

	public Long getActorMemberId() {
		return actorMemberId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
