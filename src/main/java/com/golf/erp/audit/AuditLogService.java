package com.golf.erp.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.golf.erp.audit.AuditLogDtos.AuditLogResponse;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 감사 로그 조회(읽기 전용) */
@Service
@Transactional(readOnly = true)
public class AuditLogService {
	private final AuditLogRepository repo;
	private final ObjectMapper json;

	public AuditLogService(AuditLogRepository repo, ObjectMapper json) {
		this.repo = repo;
		this.json = json;
	}

	/**
	 * @param entityType 테이블 이름(정확히 일치, 예: reservation)
	 * @param actor      직원 아이디 부분 일치
	 * @param action     정확히 일치(예: CANCEL)
	 * @param from       기록일 시작(포함)
	 * @param to         기록일 끝(포함)
	 */
	public PageResult<AuditLogResponse> list(String entityType, Long entityId, String actor, String action,
			LocalDate from, LocalDate to, int page, int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		Specification<AuditLog> spec = Specification.allOf(Specs.eq("entityType", trim(entityType)),
				Specs.eq("entityId", entityId), Specs.like(actor, "actorUsername"), Specs.eq("action", trim(action)),
				Specs.gte("createdAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("createdAt", to == null ? null : to.plusDays(1).atStartOfDay()));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))),
				a -> AuditLogResponse.of(a, parse(a.getDetails())));
	}

	public AuditLogResponse get(long id) {
		AuditLog a = repo.findById(id).orElseThrow(() -> NotFoundException.of("감사 로그", id));
		return AuditLogResponse.of(a, parse(a.getDetails()));
	}

	/** JSON 으로 읽을 수 없으면 원문 문자열로 돌려준다. */
	private JsonNode parse(String details) {
		if (details == null || details.isBlank())
			return null;
		try {
			return json.readTree(details);
		} catch (JsonProcessingException e) {
			return TextNode.valueOf(details);
		}
	}

	private static String trim(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
