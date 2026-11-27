package com.golf.erp.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 현재 요청의 직원(admin_user). created_by, assigned_by, received_by 같은 행위자 컬럼에는 {@link #idOrNull()} 을 넣는다.
 */
public final class CurrentUser {
	private CurrentUser() {
	}

	public static Optional<AdminPrincipal> get() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AdminPrincipal p)
			return Optional.of(p);
		return Optional.empty();
	}

	public static AdminPrincipal require() {
		return get().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
	}

	/** 행위자 admin_user_id. 배치·초기화 등 로그인 없이 실행되면 null */
	public static Long idOrNull() {
		return get().map(AdminPrincipal::id).orElse(null);
	}

	public static String usernameOrNull() {
		return get().map(AdminPrincipal::username).orElse(null);
	}
}
