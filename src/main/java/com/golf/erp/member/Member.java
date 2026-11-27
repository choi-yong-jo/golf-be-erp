package com.golf.erp.member;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.member. 예약 앱(golf-reservation-api)과 같은 테이블을 쓴다.
 * <ul>
 * <li>login_id·password_hash 는 예약 앱(회원 가입)이 관리한다. ERP 에서 등록하는 회원은 로그인 없는 오프라인 회원이라
 * 두 컬럼을 쓰지 않는다(login_id 는 조회만, password_hash 는 매핑하지 않음).</li>
 * <li>email 은 소문자로 정규화해 저장한다(uq_member_email).</li>
 * </ul>
 */
@Entity
@Table(name = "member")
public class Member {
	public static final String DEFAULT_GRADE = "GENERAL";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long id;
	@Column(name = "member_no", length = 30)
	private String memberNo;
	@Column(name = "login_id", length = 100, insertable = false, updatable = false)
	private String loginId;
	@Column(name = "member_name", nullable = false, length = 100)
	private String name;
	@Column(name = "phone", length = 30)
	private String phone;
	@Column(name = "email", length = 255)
	private String email;
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 10)
	private MemberRole role = MemberRole.USER;
	@Enumerated(EnumType.STRING)
	@Column(name = "member_status", nullable = false, length = 20)
	private MemberStatus status = MemberStatus.ACTIVE;
	@Column(name = "birth_date")
	private LocalDate birthDate;
	@Column(name = "gender", length = 20)
	private String gender;
	@Column(name = "address", length = 500)
	private String address;
	@Column(name = "member_grade", nullable = false, length = 30)
	private String grade = DEFAULT_GRADE;
	@Column(name = "marketing_consent", nullable = false)
	private boolean marketingConsent;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Member() {
	}

	/** 오프라인 회원 등록. 상세 항목은 {@link #update} 로 채운다. */
	public Member(LocalDateTime now) {
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String memberNo, String name, String phone, String email, LocalDate birthDate, String gender,
			String address, String grade, boolean marketingConsent, LocalDateTime now) {
		this.memberNo = memberNo;
		this.name = name;
		this.phone = phone;
		this.email = email;
		this.birthDate = birthDate;
		this.gender = gender;
		this.address = address;
		this.grade = grade == null ? DEFAULT_GRADE : grade;
		this.marketingConsent = marketingConsent;
		this.updatedAt = now;
	}

	/** 저장 후 자동 발급한 회원번호를 넣는다. */
	public void assignMemberNo(String memberNo) {
		this.memberNo = memberNo;
	}

	public void changeStatus(MemberStatus status, LocalDateTime now) {
		this.status = status;
		this.updatedAt = now;
	}

	/** 예약 앱에서 가입한(로그인 계정이 있는) 회원인지 */
	public boolean isOnline() {
		return loginId != null;
	}

	public Long getId() {
		return id;
	}

	public String getMemberNo() {
		return memberNo;
	}

	public String getLoginId() {
		return loginId;
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

	public MemberRole getRole() {
		return role;
	}

	public MemberStatus getStatus() {
		return status;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public String getGender() {
		return gender;
	}

	public String getAddress() {
		return address;
	}

	public String getGrade() {
		return grade;
	}

	public boolean isMarketingConsent() {
		return marketingConsent;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
