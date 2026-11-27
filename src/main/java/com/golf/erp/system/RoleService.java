package com.golf.erp.system;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.system.SystemDtos.PermissionResponse;
import com.golf.erp.system.SystemDtos.RoleCreateRequest;
import com.golf.erp.system.SystemDtos.RoleResponse;
import com.golf.erp.system.SystemDtos.RoleUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleService {
	private final SystemRoleRepository roles;
	private final PermissionRepository permissions;
	private final AuditLogger audit;

	public RoleService(SystemRoleRepository roles, PermissionRepository permissions, AuditLogger audit) {
		this.roles = roles;
		this.permissions = permissions;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<RoleResponse> list() {
		return roles.findAllByOrderByCodeAsc().stream().map(RoleResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public RoleResponse get(long id) {
		return RoleResponse.of(find(id));
	}

	public RoleResponse create(RoleCreateRequest r) {
		if (roles.existsByCode(r.code()))
			throw new ConflictException("이미 있는 역할 코드입니다: " + r.code());
		SystemRole role = new SystemRole(r.code(), r.name().trim(), r.description());
		if (r.permissionCodes() != null)
			role.replacePermissions(resolve(r.permissionCodes()));
		roles.save(role);
		audit.log("CREATE", "system_role", role.getId(), Map.of("code", role.getCode()));
		return RoleResponse.of(role);
	}

	public RoleResponse update(long id, RoleUpdateRequest r) {
		SystemRole role = find(id);
		role.update(r.name().trim(), r.description(), r.active());
		return RoleResponse.of(role);
	}

	public RoleResponse replacePermissions(long id, Set<String> codes) {
		SystemRole role = find(id);
		role.replacePermissions(resolve(codes));
		audit.log("PERMISSION_CHANGE", "system_role", id, Map.of("permissionCodes", codes));
		return RoleResponse.of(role);
	}

	/** 역할 삭제. role_permission·admin_user_role 은 ON DELETE CASCADE 로 함께 지워진다. */
	public void delete(long id) {
		SystemRole role = find(id);
		roles.delete(role);
		audit.log("DELETE", "system_role", id, Map.of("code", role.getCode()));
	}

	@Transactional(readOnly = true)
	public List<PermissionResponse> permissions() {
		return permissions.findAllByOrderByModuleCodeAscCodeAsc().stream().map(PermissionResponse::of).toList();
	}

	private Set<Permission> resolve(Set<String> codes) {
		List<Permission> found = permissions.findByCodeIn(codes);
		if (found.size() != codes.size()) {
			Set<String> known = found.stream().map(Permission::getCode).collect(Collectors.toSet());
			throw new IllegalArgumentException(
					"없는 권한 코드입니다: " + codes.stream().filter(c -> !known.contains(c)).sorted().toList());
		}
		return new HashSet<>(found);
	}

	private SystemRole find(long id) {
		return roles.findById(id).orElseThrow(() -> NotFoundException.of("역할", id));
	}
}
