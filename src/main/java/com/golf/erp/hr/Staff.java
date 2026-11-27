package com.golf.erp.hr;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** golf.staff. 직원 인사 정보. admin_user_id 로 ERP 로그인 계정과 1:1 로 연결할 수 있다(선택). */
@Entity
@Table(name = "staff")
public class Staff {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "staff_id")
	private Long id;
	@Column(name = "admin_user_id", unique = true)
	private Long adminUserId;
	@Column(name = "staff_no", nullable = false, unique = true, length = 30)
	private String staffNo;
	@Column(name = "staff_name", nullable = false, length = 100)
	private String name;
	@Column(name = "department", length = 100)
	private String department;
	@Column(name = "position_name", length = 100)
	private String positionName;
	@Column(name = "phone", length = 30)
	private String phone;
	@Column(name = "email", length = 255)
	private String email;
	@Column(name = "hire_date")
	private LocalDate hireDate;
	@Enumerated(EnumType.STRING)
	@Column(name = "employment_status", nullable = false, length = 20)
	private StaffStatus status = StaffStatus.ACTIVE;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Staff() {
	}

	public Staff(LocalDateTime now) {
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String staffNo, String name, Long adminUserId, String department, String positionName,
			String phone, String email, LocalDate hireDate, LocalDateTime now) {
		this.staffNo = staffNo;
		this.name = name;
		this.adminUserId = adminUserId;
		this.department = department;
		this.positionName = positionName;
		this.phone = phone;
		this.email = email;
		this.hireDate = hireDate;
		this.updatedAt = now;
	}

	public void changeStatus(StaffStatus status, LocalDateTime now) {
		this.status = status;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getAdminUserId() {
		return adminUserId;
	}

	public String getStaffNo() {
		return staffNo;
	}

	public String getName() {
		return name;
	}

	public String getDepartment() {
		return department;
	}

	public String getPositionName() {
		return positionName;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	public LocalDate getHireDate() {
		return hireDate;
	}

	public StaffStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
