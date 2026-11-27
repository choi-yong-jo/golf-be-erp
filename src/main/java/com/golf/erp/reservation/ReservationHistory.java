package com.golf.erp.reservation;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.reservation_history. 예약이 바뀔 때마다 한 줄씩 남긴다(예약 앱도 같은 테이블에 쓴다).
 * ERP 의 action_type: CREATE, CONFIRM, PAYMENT, CANCEL, COMPLETE, NO_SHOW, MEMO.
 * actor_member_id 는 회원이 한 행동일 때만 채운다(ERP 직원 행동은 null, 직원 아이디는 reason 앞에 남긴다).
 */
@Entity
@Table(name = "reservation_history")
public class ReservationHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_history_id")
	private Long id;
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
	@Column(name = "reason", length = 1000)
	private String reason;
	@Column(name = "actor_member_id")
	private Long actorMemberId;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReservationHistory() {
	}

	/**
	 * @param after        변경 후 예약
	 * @param beforeStatus 변경 전 상태(생성이면 null). 티타임·금액은 ERP 처리에서 바뀌지 않으므로 변경 후 값과 같게 남긴다.
	 */
	public static ReservationHistory of(String actionType, Reservation after, ReservationStatus beforeStatus,
			String reason, LocalDateTime now) {
		ReservationHistory h = new ReservationHistory();
		h.reservationId = after.getId();
		h.actionType = actionType;
		h.beforeStatus = beforeStatus == null ? null : beforeStatus.name();
		h.afterStatus = after.getStatus().name();
		h.beforeTeeTimeId = beforeStatus == null ? null : after.getTeeTimeId();
		h.afterTeeTimeId = after.getTeeTimeId();
		h.beforeAmount = beforeStatus == null ? null : after.getTotalAmount();
		h.afterAmount = after.getTotalAmount();
		h.reason = reason == null || reason.length() <= 1000 ? reason : reason.substring(0, 1000);
		h.createdAt = now;
		return h;
	}

	public Long getId() {
		return id;
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
