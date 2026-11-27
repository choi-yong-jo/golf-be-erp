package com.golf.erp.accounting;

import jakarta.persistence.*;

/** golf.account. 계정과목표. parent_account_id 로 계층을 이룬다(같은 유형끼리). */
@Entity
@Table(name = "account")
public class Account {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "account_id")
	private Long id;
	@Column(name = "account_code", nullable = false, unique = true, length = 30)
	private String code;
	@Column(name = "account_name", nullable = false, length = 120)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, length = 20)
	private AccountType type;
	@Column(name = "parent_account_id")
	private Long parentAccountId;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected Account() {
	}

	public Account(AccountType type) {
		this.type = type;
	}

	public void update(String code, String name, AccountType type, Long parentAccountId) {
		this.code = code;
		this.name = name;
		this.type = type;
		this.parentAccountId = parentAccountId;
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
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

	public AccountType getType() {
		return type;
	}

	public Long getParentAccountId() {
		return parentAccountId;
	}

	public boolean isActive() {
		return active;
	}
}
