package com.golf.erp.system;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** golf.admin_user_role — 직원 계정에 부여한 역할 */
@Entity
@Table(name = "admin_user_role")
@IdClass(AdminUserRole.Key.class)
public class AdminUserRole {
	@Id
	@Column(name = "admin_user_id")
	private Long adminUserId;
	@Id
	@Column(name = "system_role_id")
	private Long systemRoleId;
	@Column(name = "assigned_at", nullable = false)
	private LocalDateTime assignedAt;
	@Column(name = "assigned_by")
	private Long assignedBy;

	protected AdminUserRole() {
	}

	public AdminUserRole(Long adminUserId, Long systemRoleId, LocalDateTime assignedAt, Long assignedBy) {
		this.adminUserId = adminUserId;
		this.systemRoleId = systemRoleId;
		this.assignedAt = assignedAt;
		this.assignedBy = assignedBy;
	}

	public Long getAdminUserId() {
		return adminUserId;
	}

	public Long getSystemRoleId() {
		return systemRoleId;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public Long getAssignedBy() {
		return assignedBy;
	}

	public static class Key implements Serializable {
		private Long adminUserId;
		private Long systemRoleId;

		public Key() {
		}

		public Key(Long adminUserId, Long systemRoleId) {
			this.adminUserId = adminUserId;
			this.systemRoleId = systemRoleId;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && Objects.equals(adminUserId, k.adminUserId)
					&& Objects.equals(systemRoleId, k.systemRoleId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(adminUserId, systemRoleId);
		}
	}
}
