package com.golf.erp.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {
	List<AuditLogRow> search(@Param("entityType") String entityType, @Param("entityId") Long entityId,
			@Param("offset") int offset, @Param("size") int size);

	long count(@Param("entityType") String entityType, @Param("entityId") Long entityId);
}
