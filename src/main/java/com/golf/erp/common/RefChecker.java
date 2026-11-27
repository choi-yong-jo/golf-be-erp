package com.golf.erp.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 다른 모듈 테이블의 행 존재 확인. 없으면 404(NotFoundException). */
@Component
public class RefChecker {
	private final JdbcTemplate jdbc;

	public RefChecker(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean exists(Ref ref, Long id) {
		if (id == null)
			return false;
		Boolean found = jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM " + ref.table + " WHERE " + ref.idColumn + " = ?)", Boolean.class, id);
		return Boolean.TRUE.equals(found);
	}

	/** id 가 null 이면 통과(선택 참조), 값이 있는데 행이 없으면 404 */
	public void requireIfPresent(Ref ref, Long id) {
		if (id != null)
			require(ref, id);
	}

	public void require(Ref ref, Long id) {
		if (id == null)
			throw new IllegalArgumentException(ref.label + " id 가 필요합니다.");
		if (!exists(ref, id))
			throw NotFoundException.of(ref.label, id);
	}
}
