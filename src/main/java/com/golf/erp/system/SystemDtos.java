package com.golf.erp.system;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 인증·직원 계정·역할·권한 API 요청/응답 */
public final class SystemDtos {
	private SystemDtos() {
	}

	// ---- 인증 ----

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}

	public record ChangePasswordRequest(@NotBlank String currentPassword,
			@NotBlank @Size(min = 8, max = 100) String newPassword) {
	}

	public record TokenResponse(String tokenType, String accessToken, long accessTokenExpiresIn, String refreshToken,
			long refreshTokenExpiresIn, Me user) {
	}

	/** 로그인한 직원 정보. permissions 로 화면 메뉴·버튼을 제어한다(ADMIN 은 모든 권한). */
	public record Me(long id, String username, String displayName, String role, boolean admin,
			Set<String> permissions) {
	}

	// ---- 직원 계정 ----

	public record AdminUserCreateRequest(
			@NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "영문, 숫자, . _ - 만 쓸 수 있습니다.") String username,
			@NotBlank @Size(min = 8, max = 100) String password, @NotBlank @Size(max = 80) String displayName,
			@NotBlank String role, @Email @Size(max = 255) String email, @Size(max = 30) String phone,
			@Size(max = 50) String department, List<Long> roleIds) {
	}

	public record AdminUserUpdateRequest(@NotBlank @Size(max = 80) String displayName, @NotBlank String role,
			@Email @Size(max = 255) String email, @Size(max = 30) String phone, @Size(max = 50) String department) {
	}

	public record ActiveRequest(@NotNull Boolean active) {
	}

	public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {
	}

	public record RoleIdsRequest(@NotNull List<Long> roleIds) {
	}

	public record AdminUserResponse(Long id, String username, String displayName, String role, boolean active,
			String email, String phone, String department, LocalDateTime lastLoginAt, LocalDateTime createdAt,
			LocalDateTime updatedAt, List<RoleSummary> roles) {

		static AdminUserResponse of(AdminUser u, List<RoleSummary> roles) {
			return new AdminUserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive(),
					u.getEmail(), u.getPhone(), u.getDepartment(), u.getLastLoginAt(), u.getCreatedAt(),
					u.getUpdatedAt(), roles);
		}
	}

	// ---- 역할·권한 ----

	public record RoleCreateRequest(
			@NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z0-9_]+$", message = "영문 대문자, 숫자, _ 만 쓸 수 있습니다.") String code,
			@NotBlank @Size(max = 100) String name, @Size(max = 500) String description, Set<String> permissionCodes) {
	}

	public record RoleUpdateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
			@NotNull Boolean active) {
	}

	public record PermissionCodesRequest(@NotNull Set<String> permissionCodes) {
	}

	public record RoleSummary(Long id, String code, String name, boolean active) {
		static RoleSummary of(SystemRole r) {
			return new RoleSummary(r.getId(), r.getCode(), r.getName(), r.isActive());
		}
	}

	public record RoleResponse(Long id, String code, String name, String description, boolean active,
			List<String> permissionCodes) {
		static RoleResponse of(SystemRole r) {
			return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isActive(),
					r.getPermissions().stream().map(Permission::getCode).sorted().toList());
		}
	}

	public record PermissionResponse(Long id, String code, String name, String moduleCode) {
		static PermissionResponse of(Permission p) {
			return new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getModuleCode());
		}
	}
}
