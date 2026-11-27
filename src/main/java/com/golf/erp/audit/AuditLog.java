package com.golf.erp.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** golf.audit_log (읽기 전용). 기록은 common.AuditLogger 가 SQL 로 한다. details 는 JSONB 원문 문자열 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "audit_log_id")
	private Long id;
	@Column(name = "actor_username", length = 60)
	private String actorUsername;
	@Column(name = "action", nullable = false, length = 80)
	private String action;
	@Column(name = "entity_type", nullable = false, length = 80)
	private String entityType;
	@Column(name = "entity_id")
	private Long entityId;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "details")
	private String details;
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected AuditLog() {
	}

	public Long getId() {
		return id;
	}

	public String getActorUsername() {
		return actorUsername;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public String getDetails() {
		return details;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
