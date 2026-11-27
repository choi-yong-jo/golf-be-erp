package com.golf.erp.security;

import java.util.Set;

/** 로그인한 직원. 요청마다 DB 에서 다시 읽으므로 비활성화·권한 변경이 즉시 반영된다. */
public record AdminPrincipal(long id, String username, String displayName, String role, Set<String> permissions) {

	public boolean isAdmin() {
		return "ADMIN".equals(role);
	}
}
