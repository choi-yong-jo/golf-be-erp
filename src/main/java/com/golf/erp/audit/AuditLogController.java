package com.golf.erp.audit;

import com.golf.erp.common.PageResult;
import org.springframework.web.bind.annotation.*;

/** 감사 로그 조회 (읽기 전용) */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
	private final AuditLogMapper mapper;

	public AuditLogController(AuditLogMapper mapper) {
		this.mapper = mapper;
	}

	@GetMapping
	public PageResult<AuditLogRow> search(@RequestParam(required = false) String entityType,
			@RequestParam(required = false) Long entityId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		PageResult.validate(page, size);
		return new PageResult<>(mapper.search(entityType, entityId, page * size, size), page, size,
				mapper.count(entityType, entityId));
	}
}
