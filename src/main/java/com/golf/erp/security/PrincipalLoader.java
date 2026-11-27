package com.golf.erp.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** admin_user + 역할(admin_user_role → system_role → role_permission → permission)에서 권한을 읽는다. */
@Component
public class PrincipalLoader {
	private final JdbcTemplate jdbc;

	public PrincipalLoader(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 활성 계정만 돌려준다. 없거나 비활성이면 empty */
	public Optional<AdminPrincipal> loadActive(long adminUserId) {
		List<String[]> rows = jdbc.query(
				"SELECT username, display_name, role FROM golf.admin_user WHERE admin_user_id = ? AND is_active",
				(rs, i) -> new String[] { rs.getString(1), rs.getString(2), rs.getString(3) }, adminUserId);
		if (rows.isEmpty())
			return Optional.empty();
		String[] u = rows.get(0);
		Set<String> permissions = new HashSet<>(jdbc.queryForList("""
				SELECT DISTINCT p.permission_code
				  FROM golf.admin_user_role ur
				  JOIN golf.system_role r      ON r.system_role_id = ur.system_role_id AND r.is_active
				  JOIN golf.role_permission rp ON rp.system_role_id = r.system_role_id
				  JOIN golf.permission p       ON p.permission_id = rp.permission_id
				 WHERE ur.admin_user_id = ?
				""", String.class, adminUserId));
		return Optional.of(new AdminPrincipal(adminUserId, u[0], u[1], u[2], Set.copyOf(permissions)));
	}
}
