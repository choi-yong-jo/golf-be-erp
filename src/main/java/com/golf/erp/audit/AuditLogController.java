package com.golf.erp.audit;

import com.golf.erp.audit.AuditLogDtos.AuditLogResponse;
import com.golf.erp.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 감사 로그 조회(읽기 전용) */
@Tag(name = "감사 로그")
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
	private final AuditLogService service;

	public AuditLogController(AuditLogService service) {
		this.service = service;
	}

	@Operation(summary = "감사 로그 목록", description = "entityType: 테이블 이름(예: reservation), actor: 직원 아이디 부분 일치, from/to: 기록일(양 끝 포함)")
	@GetMapping
	public PageResult<AuditLogResponse> list(@RequestParam(required = false) String entityType,
			@RequestParam(required = false) Long entityId, @RequestParam(required = false) String actor,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(entityType, entityId, actor, action, from, to, page, size);
	}

	@GetMapping("/{id}")
	public AuditLogResponse get(@PathVariable long id) {
		return service.get(id);
	}
}
