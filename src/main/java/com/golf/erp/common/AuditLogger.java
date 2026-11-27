package com.golf.erp.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.golf.erp.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * golf.audit_log 기록. 삭제·승인·마감·권한 변경처럼 되돌리기 어렵거나 책임 추적이 필요한 작업에서 호출한다.
 * 호출한 서비스의 트랜잭션에 함께 묶인다(업무가 롤백되면 로그도 남지 않음).
 */
@Component
public class AuditLogger {
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final Clock clock;

	public AuditLogger(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * @param action     예: CREATE, UPDATE, DELETE, STATUS_CHANGE, APPROVE, CLOSE, REOPEN
	 * @param entityType 테이블 이름(예: round, payroll)
	 * @param details    추가 정보(JSON 으로 저장), null 가능
	 */
	public void log(String action, String entityType, Long entityId, Map<String, ?> details) {
		jdbc.update("INSERT INTO golf.audit_log (actor_username, action, entity_type, entity_id, details, created_at)"
				+ " VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)", CurrentUser.usernameOrNull(), action, entityType, entityId,
				toJson(details), LocalDateTime.now(clock));
	}

	private String toJson(Map<String, ?> details) {
		if (details == null || details.isEmpty())
			return null;
		try {
			return json.writeValueAsString(details);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}
}
