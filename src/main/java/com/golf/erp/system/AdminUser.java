package com.golf.erp.system;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.admin_user — ERP 직원 로그인 계정 */
@Entity
@Table(name = "admin_user")
public class AdminUser {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "admin_user_id")
	private Long id;
	@Column(nullable = false, length = 60)
	private String username;
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;
	@Column(name = "display_name", nullable = false, length = 80)
	private String displayName;
	/** AdminRole 이름. 예약 앱 등 다른 시스템이 만든 값이 있을 수 있어 문자열로 둔다. */
	@Column(nullable = false, length = 30)
	private String role;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(length = 255)
	private String email;
	@Column(length = 30)
	private String phone;
	@Column(length = 50)
	private String department;
	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected AdminUser() {
	}

	public AdminUser(String username, String passwordHash, String displayName, AdminRole role, String email,
			String phone, String department, LocalDateTime now) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.role = role.name();
		this.email = email;
		this.phone = phone;
		this.department = department;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String displayName, AdminRole role, String email, String phone, String department,
			LocalDateTime now) {
		this.displayName = displayName;
		this.role = role.name();
		this.email = email;
		this.phone = phone;
		this.department = department;
		this.updatedAt = now;
	}

	public void changePassword(String passwordHash, LocalDateTime now) {
		this.passwordHash = passwordHash;
		this.updatedAt = now;
	}

	public void changeActive(boolean active, LocalDateTime now) {
		this.active = active;
		this.updatedAt = now;
	}

	public void loggedIn(LocalDateTime now) {
		this.lastLoginAt = now;
	}

	public boolean isAdmin() {
		return AdminRole.ADMIN.name().equals(role);
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getDepartment() {
		return department;
	}

	public LocalDateTime getLastLoginAt() {
		return lastLoginAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
