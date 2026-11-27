package com.golf.erp.member;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.reservation_restriction. [startsAt, endsAt) 동안 해당 회원의 예약 생성을 막는다(endsAt null = 무기한).
 * restrictionType 은 CHECK 가 없는 자유 코드다(예: NO_SHOW, PAYMENT_DEFAULT, MANUAL). 예약 앱도 같은 행을 본다.
 */
@Entity
@Table(name = "reservation_restriction")
public class ReservationRestriction {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "restriction_id")
	private Long id;
	@Column(name = "member_id", nullable = false)
	private Long memberId;
	@Column(name = "restriction_type", nullable = false, length = 30)
	private String restrictionType;
	@Column(name = "starts_at", nullable = false)
	private LocalDateTime startsAt;
	@Column(name = "ends_at")
	private LocalDateTime endsAt;
	@Column(name = "reason", nullable = false, length = 1000)
	private String reason;
	@Column(name = "created_by")
	private Long createdBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReservationRestriction() {
	}

	public ReservationRestriction(Long memberId, String restrictionType, LocalDateTime startsAt,
			LocalDateTime endsAt, String reason, Long createdBy, LocalDateTime now) {
		if (endsAt != null && !endsAt.isAfter(startsAt))
			throw new IllegalArgumentException("제한 종료 시각은 시작 시각 이후여야 합니다.");
		this.memberId = memberId;
		this.restrictionType = restrictionType;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.reason = reason;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public boolean isActiveAt(LocalDateTime at) {
		return !at.isBefore(startsAt) && (endsAt == null || at.isBefore(endsAt));
	}

	public boolean isEndedAt(LocalDateTime at) {
		return endsAt != null && !endsAt.isAfter(at);
	}

	public boolean isFutureAt(LocalDateTime at) {
		return startsAt.isAfter(at);
	}

	/**
	 * 진행 중인 제한을 지금 끝낸다. ck_restriction_period(ends_at > starts_at) 때문에 시작 시각과 같으면 1초 뒤로 둔다.
	 */
	public void end(LocalDateTime now) {
		if (isEndedAt(now))
			throw new ConflictException("이미 종료된 예약 제한입니다.");
		if (isFutureAt(now))
			throw new ConflictException("아직 시작되지 않은 예약 제한입니다.");
		this.endsAt = now.isAfter(startsAt) ? now : startsAt.plusSeconds(1);
	}

	public Long getId() {
		return id;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getRestrictionType() {
		return restrictionType;
	}

	public LocalDateTime getStartsAt() {
		return startsAt;
	}

	public LocalDateTime getEndsAt() {
		return endsAt;
	}

	public String getReason() {
		return reason;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
