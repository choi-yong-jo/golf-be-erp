package com.golf.erp.system;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/** golf.system_role — 권한 묶음. role_permission 으로 permission 과 연결된다. */
@Entity
@Table(name = "system_role")
public class SystemRole {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "system_role_id")
	private Long id;
	@Column(name = "role_code", nullable = false, length = 40)
	private String code;
	@Column(name = "role_name", nullable = false, length = 100)
	private String name;
	@Column(length = 500)
	private String description;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@ManyToMany
	@JoinTable(name = "role_permission", joinColumns = @JoinColumn(name = "system_role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new HashSet<>();

	protected SystemRole() {
	}

	public SystemRole(String code, String name, String description) {
		this.code = code;
		this.name = name;
		this.description = description;
	}

	public void update(String name, String description, boolean active) {
		this.name = name;
		this.description = description;
		this.active = active;
	}

	public void replacePermissions(Set<Permission> permissions) {
		this.permissions.clear();
		this.permissions.addAll(permissions);
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}

	public Set<Permission> getPermissions() {
		return permissions;
	}
}
