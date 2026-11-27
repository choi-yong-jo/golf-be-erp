package com.golf.erp.resource;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.caddie. DDL 에 같은 뜻의 컬럼이 두 벌 있다(hired_on/hire_date, caddie_status/employment_status).
 * 앞의 것을 기준으로 쓰고, 뒤의 것은 다른 시스템이 읽어도 맞도록 같은 값으로 함께 저장한다.
 */
@Entity
@Table(name = "caddie")
public class Caddie {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "caddie_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "caddie_no", nullable = false, length = 20)
	private String caddieNo;
	@Column(name = "caddie_name", nullable = false, length = 100)
	private String name;
	@Column(length = 30)
	private String phone;
	@Column(length = 10)
	private String grade;
	@Enumerated(EnumType.STRING)
	@Column(name = "caddie_status", nullable = false, length = 20)
	private CaddieStatus status = CaddieStatus.ACTIVE;
	@Column(name = "hired_on")
	private LocalDate hiredOn;
	@Column(length = 500)
	private String memo;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	// 중복 컬럼(동기화용, API 에 노출하지 않음)
	@Column(name = "hire_date")
	private LocalDate hireDate;
	@Column(name = "employment_status", nullable = false, length = 20)
	private String employmentStatus = CaddieStatus.ACTIVE.name();

	protected Caddie() {
	}

	public Caddie(Long golfCourseId, String caddieNo, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.caddieNo = caddieNo;
		this.createdAt = now;
	}

	public void update(String name, String phone, String grade, LocalDate hiredOn, String memo, LocalDateTime now) {
		this.name = name;
		this.phone = phone;
		this.grade = grade;
		this.hiredOn = hiredOn;
		this.hireDate = hiredOn;
		this.memo = memo;
		this.updatedAt = now;
	}

	public void changeStatus(CaddieStatus status, LocalDateTime now) {
		this.status = status;
		this.employmentStatus = status.name();
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public String getCaddieNo() {
		return caddieNo;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

	public String getGrade() {
		return grade;
	}

	public CaddieStatus getStatus() {
		return status;
	}

	public LocalDate getHiredOn() {
		return hiredOn;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
