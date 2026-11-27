package com.golf.erp.course;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.golf_course. 예약·티타임은 is_active = true 이고 course_status = OPEN 인 골프장에서만 받는다.
 * is_active=false 는 목록에서 숨기는 비활성(미사용) 처리다.
 */
@Entity
@Table(name = "golf_course")
public class GolfCourse {
	public static final int DEFAULT_HOLE_COUNT = 18;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "golf_course_id")
	private Long id;
	@Column(name = "course_code", nullable = false, length = 30)
	private String courseCode;
	@Column(name = "course_name", nullable = false, length = 200)
	private String courseName;
	@Column(name = "address", length = 500)
	private String address;
	@Column(name = "phone", length = 30)
	private String phone;
	@Column(name = "hole_count")
	private Integer holeCount = DEFAULT_HOLE_COUNT;
	@Enumerated(EnumType.STRING)
	@Column(name = "course_status", nullable = false, length = 20)
	private GolfCourseStatus status = GolfCourseStatus.OPEN;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected GolfCourse() {
	}

	public GolfCourse(String courseCode, LocalDateTime now) {
		this.courseCode = courseCode;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String courseCode, String courseName, String address, String phone, int holeCount,
			LocalDateTime now) {
		this.courseCode = courseCode;
		this.courseName = courseName;
		this.address = address;
		this.phone = phone;
		this.holeCount = holeCount;
		this.updatedAt = now;
	}

	public void changeStatus(GolfCourseStatus status, boolean active, LocalDateTime now) {
		this.status = status;
		this.active = active;
		this.updatedAt = now;
	}

	/** 예약·티타임 생성이 가능한 상태 */
	public boolean isOperating() {
		return active && status == GolfCourseStatus.OPEN;
	}

	public Long getId() {
		return id;
	}

	public String getCourseCode() {
		return courseCode;
	}

	public String getCourseName() {
		return courseName;
	}

	public String getAddress() {
		return address;
	}

	public String getPhone() {
		return phone;
	}

	public Integer getHoleCount() {
		return holeCount;
	}

	public GolfCourseStatus getStatus() {
		return status;
	}

	public boolean isActive() {
		return active;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
