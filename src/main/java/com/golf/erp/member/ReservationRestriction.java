package com.golf.erp.member;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * golf.reservation_restriction. [startsAt, endsAt) 동안 해당 회원의 ERP 예약 생성을 막는다(endsAt null = 무기한).
 * restrictionType 예: NO_SHOW, PAYMENT_DEFAULT, MANUAL
 */
@Entity
@Table(name = "reservation_restriction")
public class ReservationRestriction {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "restriction_id")
	private Long restrictionId;
	@Column(name = "member_id", nullable = false)
	private Long memberId;
	@Column(name = "restriction_type", nullable = false, length = 30)
	private String restrictionType;
	@Column(name = "starts_at", nullable = false)
	private LocalDateTime startsAt;
	@Column(name = "ends_at")
	private LocalDateTime endsAt;
	@Column(nullable = false, length = 1000)
	private String reason;
	@Column(name = "created_by")
	private Long createdBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected ReservationRestriction() {
	}

	public ReservationRestriction(Long memberId, String restrictionType, LocalDateTime startsAt,
			LocalDateTime endsAt, String reason, LocalDateTime now) {
		this.memberId = memberId;
		this.restrictionType = restrictionType;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.reason = reason;
		this.createdAt = now;
	}

	public boolean isActiveAt(LocalDateTime at) {
		return !at.isBefore(startsAt) && (endsAt == null || at.isBefore(endsAt));
	}

	public void endAt(LocalDateTime endsAt) {
		this.endsAt = endsAt;
	}

	public Long getRestrictionId() {
		return restrictionId;
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
