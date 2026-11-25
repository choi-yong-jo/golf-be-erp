package com.golf.erp.member;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** golf.member. login_id/password_hash 는 예약 앱(회원 가입)이 관리하므로 ERP에서는 쓰지 않는다. */
@Entity
@Table(name = "member")
public class Member {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long memberId;
	@Column(name = "member_no", length = 30)
	private String memberNo;
	@Column(name = "login_id", length = 100, insertable = false, updatable = false)
	private String loginId;
	@Column(name = "member_name", nullable = false, length = 100)
	private String memberName;
	@Column(length = 30)
	private String phone;
	@Column(length = 255)
	private String email;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MemberRole role = MemberRole.USER;
	@Enumerated(EnumType.STRING)
	@Column(name = "member_status", nullable = false, length = 20)
	private MemberStatus memberStatus = MemberStatus.ACTIVE;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Member() {
	}

	public Member(String memberNo, String memberName, String phone, String email, LocalDateTime now) {
		this.memberNo = memberNo;
		this.memberName = memberName;
		this.phone = phone;
		this.email = email;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String memberNo, String memberName, String phone, String email, LocalDateTime now) {
		this.memberNo = memberNo;
		this.memberName = memberName;
		this.phone = phone;
		this.email = email;
		this.updatedAt = now;
	}

	public void changeStatus(MemberStatus status, LocalDateTime now) {
		this.memberStatus = status;
		this.updatedAt = now;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getMemberNo() {
		return memberNo;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getMemberName() {
		return memberName;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	public MemberRole getRole() {
		return role;
	}

	public MemberStatus getMemberStatus() {
		return memberStatus;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
