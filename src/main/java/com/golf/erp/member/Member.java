package com.golf.erp.member;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
public class Member {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "member_no", nullable = false, unique = true, length = 30)
	private String memberNo;
	@Column(nullable = false, length = 80)
	private String name;
	@Column(length = 30)
	private String phone;
	@Column(length = 120)
	private String email;
	@Column(nullable = false, length = 20)
	private String status = "ACTIVE";
	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Member() {
	}

	public Member(String memberNo, String name, String phone, String email) {
		this.memberNo = memberNo;
		this.name = name;
		this.phone = phone;
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public String getMemberNo() {
		return memberNo;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	public String getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void update(String name, String phone, String email, String status) {
		this.name = name;
		this.phone = phone;
		this.email = email;
		this.status = status;
	}
}
