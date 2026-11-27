package com.golf.erp.system;

import com.golf.erp.config.ErpProperties;
import com.golf.erp.security.ErpModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 기동 시 기본 데이터.
 * <ol>
 * <li>ErpModule 마다 {MODULE}_READ / {MODULE}_WRITE 권한을 golf.permission 에 등록(이미 있으면 그대로)</li>
 * <li>admin_user 가 하나도 없으면 ADMIN 계정 생성(erp.bootstrap.*)</li>
 * </ol>
 */
@Component
public class SystemDataInitializer implements ApplicationRunner {
	private static final Logger log = LoggerFactory.getLogger(SystemDataInitializer.class);

	private final JdbcTemplate jdbc;
	private final PasswordEncoder passwordEncoder;
	private final ErpProperties props;
	private final Clock clock;

	public SystemDataInitializer(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, ErpProperties props,
			Clock clock) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.props = props;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		int added = 0;
		for (ErpModule m : ErpModule.values()) {
			added += upsertPermission(m.readAuthority(), m.label + " 조회", m.name());
			added += upsertPermission(m.writeAuthority(), m.label + " 등록·수정", m.name());
		}
		if (added > 0)
			log.info("권한 {}건 등록", added);
		removeObsoletePermissions();
		bootstrapAdmin();
	}

	private int upsertPermission(String code, String name, String module) {
		return jdbc.update("INSERT INTO golf.permission (permission_code, permission_name, module_code) VALUES (?, ?, ?)"
				+ " ON CONFLICT (permission_code) DO NOTHING", code, name, module);
	}

	/**
	 * 없어진 모듈(예: DDL 변경으로 삭제된 FNB)의 권한 삭제. role_permission 은 ON DELETE CASCADE 로 함께 지워진다.
	 * ErpModule 이 만든 형식({MODULE}_READ/_WRITE)만 대상으로 하고, 직접 넣은 다른 권한 코드는 건드리지 않는다.
	 */
	private void removeObsoletePermissions() {
		String modules = java.util.Arrays.stream(ErpModule.values()).map(m -> "'" + m.name() + "'")
				.collect(java.util.stream.Collectors.joining(","));
		int removed = jdbc.update("DELETE FROM golf.permission WHERE module_code NOT IN (" + modules + ")"
				+ " AND permission_code IN (module_code || '_READ', module_code || '_WRITE')");
		if (removed > 0)
			log.warn("없어진 모듈의 권한 {}건 삭제(역할 연결 포함)", removed);
	}

	private void bootstrapAdmin() {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM golf.admin_user", Integer.class);
		if (count != null && count > 0)
			return;
		String username = props.bootstrap().adminUsername();
		String password = props.bootstrap().adminPassword();
		boolean generated = password == null || password.isBlank();
		if (generated)
			password = randomPassword();
		LocalDateTime now = LocalDateTime.now(clock);
		jdbc.update("INSERT INTO golf.admin_user (username, password_hash, display_name, role, is_active, created_at, updated_at)"
				+ " VALUES (?, ?, ?, 'ADMIN', TRUE, ?, ?)", username, passwordEncoder.encode(password), "관리자", now, now);
		if (generated)
			log.warn("초기 관리자 계정을 만들었습니다. username={} password={} (로그인 후 바로 변경하세요)", username, password);
		else
			log.warn("초기 관리자 계정을 만들었습니다. username={} (erp.bootstrap.admin-password 로 지정한 비밀번호)", username);
	}

	private static String randomPassword() {
		byte[] b = new byte[12];
		new SecureRandom().nextBytes(b);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
	}
}
