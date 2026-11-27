package com.golf.erp.round;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.round_assignment — 캐디·카트 배정 이력. released_at 이 NULL 이면 지금 배정 중이다.
 * 캐디 1명·카트 1대는 동시에 한 라운드에만 배정된다(부분 유니크 인덱스).
 */
@Entity
@Table(name = "round_assignment")
public class RoundAssignment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_assignment_id")
	private Long id;
	@Column(name = "round_id", nullable = false)
	private Long roundId;
	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 10)
	private ResourceType resourceType;
	@Column(name = "caddie_id")
	private Long caddieId;
	@Column(name = "cart_id")
	private Long cartId;
	@Column(name = "assigned_at", nullable = false)
	private LocalDateTime assignedAt;
	@Column(name = "released_at")
	private LocalDateTime releasedAt;
	@Column(name = "assigned_by")
	private Long assignedBy;
	@Column(name = "release_reason", length = 200)
	private String releaseReason;

	protected RoundAssignment() {
	}

	private RoundAssignment(Long roundId, ResourceType type, Long caddieId, Long cartId, Long assignedBy,
			LocalDateTime now) {
		this.roundId = roundId;
		this.resourceType = type;
		this.caddieId = caddieId;
		this.cartId = cartId;
		this.assignedBy = assignedBy;
		this.assignedAt = now;
	}

	public static RoundAssignment of(Long roundId, ResourceType type, long resourceId, Long assignedBy,
			LocalDateTime now) {
		return type == ResourceType.CADDIE ? new RoundAssignment(roundId, type, resourceId, null, assignedBy, now)
				: new RoundAssignment(roundId, type, null, resourceId, assignedBy, now);
	}

	void release(String reason, LocalDateTime now) {
		if (releasedAt != null)
			throw new ConflictException("이미 해제된 배정입니다.");
		this.releasedAt = now;
		this.releaseReason = reason;
	}

	public boolean isActive() {
		return releasedAt == null;
	}

	/** 캐디 또는 카트 id */
	public Long getResourceId() {
		return resourceType == ResourceType.CADDIE ? caddieId : cartId;
	}

	public Long getId() {
		return id;
	}

	public Long getRoundId() {
		return roundId;
	}

	public ResourceType getResourceType() {
		return resourceType;
	}

	public Long getCaddieId() {
		return caddieId;
	}

	public Long getCartId() {
		return cartId;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public LocalDateTime getReleasedAt() {
		return releasedAt;
	}

	public Long getAssignedBy() {
		return assignedBy;
	}

	public String getReleaseReason() {
		return releaseReason;
	}
}
