package com.golf.erp.system;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.security.CurrentUser;
import com.golf.erp.system.SystemDtos.AdminUserCreateRequest;
import com.golf.erp.system.SystemDtos.AdminUserResponse;
import com.golf.erp.system.SystemDtos.AdminUserUpdateRequest;
import com.golf.erp.system.SystemDtos.RoleSummary;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminUserService {
	private final AdminUserRepository users;
	private final SystemRoleRepository roles;
	private final AdminUserRoleRepository userRoles;
	private final PasswordEncoder passwordEncoder;
	private final AuditLogger audit;
	private final Clock clock;

	public AdminUserService(AdminUserRepository users, SystemRoleRepository roles, AdminUserRoleRepository userRoles,
			PasswordEncoder passwordEncoder, AuditLogger audit, Clock clock) {
		this.users = users;
		this.roles = roles;
		this.userRoles = userRoles;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResult<AdminUserResponse> list(String keyword, String role, Boolean active, int page, int size) {
		Specification<AdminUser> spec = Specification.allOf(Specs.like(keyword, "username", "displayName"),
				Specs.eq("role", role), Specs.eq("active", active));
		var result = users.findAll(spec, Pages.of(page, size, Sort.by("username")));
		Map<Long, List<RoleSummary>> rolesByUser = rolesOf(result.map(AdminUser::getId).getContent());
		return PageResult.of(result, u -> AdminUserResponse.of(u, rolesByUser.getOrDefault(u.getId(), List.of())));
	}

	@Transactional(readOnly = true)
	public AdminUserResponse get(long id) {
		return toResponse(find(id));
	}

	public AdminUserResponse create(AdminUserCreateRequest r) {
		String username = r.username().trim();
		if (users.existsByUsername(username))
			throw new ConflictException("이미 사용 중인 아이디입니다: " + username);
		AdminUser u = users.save(new AdminUser(username, passwordEncoder.encode(r.password()), r.displayName().trim(),
				AdminRole.parse(r.role()), blankToNull(r.email()), blankToNull(r.phone()), blankToNull(r.department()),
				now()));
		if (r.roleIds() != null && !r.roleIds().isEmpty())
			assignRoles(u.getId(), r.roleIds());
		audit.log("CREATE", "admin_user", u.getId(), Map.of("username", username, "role", u.getRole()));
		return toResponse(u);
	}

	public AdminUserResponse update(long id, AdminUserUpdateRequest r) {
		AdminUser u = find(id);
		AdminRole role = AdminRole.parse(r.role());
		if (u.isAdmin() && role != AdminRole.ADMIN)
			requireAnotherActiveAdmin(u);
		u.update(r.displayName().trim(), role, blankToNull(r.email()), blankToNull(r.phone()),
				blankToNull(r.department()), now());
		return toResponse(u);
	}

	public AdminUserResponse changeActive(long id, boolean active) {
		AdminUser u = find(id);
		if (!active) {
			if (u.getId().equals(CurrentUser.idOrNull()))
				throw new ConflictException("자기 계정은 비활성화할 수 없습니다.");
			if (u.isAdmin())
				requireAnotherActiveAdmin(u);
		}
		u.changeActive(active, now());
		audit.log("STATUS_CHANGE", "admin_user", id, Map.of("active", active));
		return toResponse(u);
	}

	public void resetPassword(long id, String newPassword) {
		find(id).changePassword(passwordEncoder.encode(newPassword), now());
		audit.log("PASSWORD_RESET", "admin_user", id, null);
	}

	public AdminUserResponse replaceRoles(long id, List<Long> roleIds) {
		AdminUser u = find(id);
		userRoles.deleteByAdminUserId(id);
		assignRoles(id, roleIds);
		audit.log("ROLE_CHANGE", "admin_user", id, Map.of("roleIds", roleIds));
		return toResponse(u);
	}

	private void assignRoles(long adminUserId, Collection<Long> roleIds) {
		var distinct = new HashSet<>(roleIds);
		var found = roles.findAllById(distinct);
		if (found.size() != distinct.size())
			throw new NotFoundException("없는 역할이 포함되어 있습니다: " + roleIds);
		LocalDateTime now = now();
		Long actor = CurrentUser.idOrNull();
		found.forEach(role -> userRoles.save(new AdminUserRole(adminUserId, role.getId(), now, actor)));
	}

	private void requireAnotherActiveAdmin(AdminUser target) {
		long admins = users.countActiveAdmins();
		if (target.isActive() && admins <= 1)
			throw new ConflictException("활성 ADMIN 계정이 최소 1개는 있어야 합니다.");
	}

	private AdminUserResponse toResponse(AdminUser u) {
		return AdminUserResponse.of(u, rolesOf(List.of(u.getId())).getOrDefault(u.getId(), List.of()));
	}

	private Map<Long, List<RoleSummary>> rolesOf(Collection<Long> userIds) {
		if (userIds.isEmpty())
			return Map.of();
		List<AdminUserRole> links = userRoles.findByAdminUserIdIn(userIds);
		Map<Long, SystemRole> roleById = roles
				.findAllById(links.stream().map(AdminUserRole::getSystemRoleId).collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(SystemRole::getId, Function.identity()));
		return links.stream().collect(Collectors.groupingBy(AdminUserRole::getAdminUserId,
				Collectors.mapping(l -> RoleSummary.of(roleById.get(l.getSystemRoleId())), Collectors.toList())));
	}

	private AdminUser find(long id) {
		return users.findById(id).orElseThrow(() -> NotFoundException.of("직원 계정", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
