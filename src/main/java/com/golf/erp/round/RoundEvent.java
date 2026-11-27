package com.golf.erp.round;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.round_event — 라운드 이용 내역(타임라인). 추가만 하고 바꾸지 않는다. */
@Entity
@Table(name = "round_event")
public class RoundEvent {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_event_id")
	private Long id;
	@Column(name = "round_id", nullable = false)
	private Long roundId;
	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 30)
	private RoundEventType type;
	@Column(name = "course_id")
	private Long courseId;
	@Column(name = "round_player_id")
	private Long roundPlayerId;
	@Column(length = 1000)
	private String message;
	@Column(name = "actor_admin_user_id")
	private Long actorAdminUserId;
	@Column(name = "occurred_at", nullable = false)
	private LocalDateTime occurredAt;

	protected RoundEvent() {
	}

	public RoundEvent(Long roundId, RoundEventType type, Long courseId, Long roundPlayerId, String message,
			Long actorAdminUserId, LocalDateTime now) {
		this.roundId = roundId;
		this.type = type;
		this.courseId = courseId;
		this.roundPlayerId = roundPlayerId;
		this.message = message;
		this.actorAdminUserId = actorAdminUserId;
		this.occurredAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getRoundId() {
		return roundId;
	}

	public RoundEventType getType() {
		return type;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Long getRoundPlayerId() {
		return roundPlayerId;
	}

	public String getMessage() {
		return message;
	}

	public Long getActorAdminUserId() {
		return actorAdminUserId;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}
}
