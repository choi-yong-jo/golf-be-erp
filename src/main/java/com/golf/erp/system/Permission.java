package com.golf.erp.system;

import jakarta.persistence.*;

/** golf.permission — {MODULE}_READ / {MODULE}_WRITE. 기동 시 ErpModule 기준으로 자동 등록된다. */
@Entity
@Table(name = "permission")
public class Permission {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "permission_id")
	private Long id;
	@Column(name = "permission_code", nullable = false, length = 80)
	private String code;
	@Column(name = "permission_name", nullable = false, length = 120)
	private String name;
	@Column(name = "module_code", nullable = false, length = 40)
	private String moduleCode;

	protected Permission() {
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

	public String getModuleCode() {
		return moduleCode;
	}
}
