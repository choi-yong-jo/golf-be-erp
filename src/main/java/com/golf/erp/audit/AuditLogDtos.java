package com.golf.erp.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 감사 로그 API 응답 */
public final class AuditLogDtos {
	private AuditLogDtos() {
	}

	/** details: 저장된 JSON 그대로(객체·배열 등, 없으면 null) */
	public record AuditLogResponse(Long id, String actorUsername, String action, String entityType, Long entityId,
			JsonNode details, LocalDateTime createdAt) {

		static AuditLogResponse of(AuditLog a, JsonNode details) {
			return new AuditLogResponse(a.getId(), a.getActorUsername(), a.getAction(), a.getEntityType(),
					a.getEntityId(), details, a.getCreatedAt());
		}
	}
}
