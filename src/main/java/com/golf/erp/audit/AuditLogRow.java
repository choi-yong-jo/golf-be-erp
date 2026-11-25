package com.golf.erp.audit;

import java.time.LocalDateTime;

/** details 는 JSONB 원문 문자열 */
public record AuditLogRow(Long auditLogId, String actorUsername, String action, String entityType, Long entityId,
		String details, LocalDateTime createdAt) {
}
