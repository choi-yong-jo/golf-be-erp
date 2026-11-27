package com.golf.erp.system;

import com.golf.erp.common.PageResult;
import com.golf.erp.system.SystemDtos.ActiveRequest;
import com.golf.erp.system.SystemDtos.AdminUserCreateRequest;
import com.golf.erp.system.SystemDtos.AdminUserResponse;
import com.golf.erp.system.SystemDtos.AdminUserUpdateRequest;
import com.golf.erp.system.SystemDtos.ResetPasswordRequest;
import com.golf.erp.system.SystemDtos.RoleIdsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "시스템 - 직원 계정")
@RestController
@RequestMapping("/api/admin-users")
public class AdminUserController {
	private final AdminUserService service;

	public AdminUserController(AdminUserService service) {
		this.service = service;
	}

	@Operation(summary = "직원 계정 목록", description = "keyword: 아이디·이름 부분 일치")
	@GetMapping
	public PageResult<AdminUserResponse> list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String role, @RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(keyword, role, active, page, size);
	}

	@GetMapping("/{id}")
	public AdminUserResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public AdminUserResponse update(@PathVariable long id, @Valid @RequestBody AdminUserUpdateRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "계정 활성/비활성", description = "비활성 계정은 즉시 모든 API 호출이 거부된다.")
	@PatchMapping("/{id}/active")
	public AdminUserResponse changeActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
		return service.changeActive(id, r.active());
	}

	@Operation(summary = "비밀번호 초기화(관리자)")
	@PostMapping("/{id}/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest r) {
		service.resetPassword(id, r.newPassword());
	}

	@Operation(summary = "역할 지정(전체 교체)")
	@PutMapping("/{id}/roles")
	public AdminUserResponse replaceRoles(@PathVariable long id, @Valid @RequestBody RoleIdsRequest r) {
		return service.replaceRoles(id, r.roleIds());
	}
}
