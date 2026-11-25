package com.golf.erp.course;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** golf.golf_course. course_status: OPEN(운영) / CLOSED(운영 중지) */
@Entity
@Table(name = "golf_course")
public class GolfCourse {
	public static final String OPEN = "OPEN";
	public static final String CLOSED = "CLOSED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "golf_course_id")
	private Long golfCourseId;
	@Column(name = "course_code", nullable = false, length = 30)
	private String courseCode;
	@Column(name = "course_name", nullable = false, length = 200)
	private String courseName;
	@Column(length = 500)
	private String address;
	@Column(length = 30)
	private String phone;
	@Column(name = "hole_count")
	private Integer holeCount;
	@Column(name = "course_status", nullable = false, length = 20)
	private String courseStatus = OPEN;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected GolfCourse() {
	}

	public GolfCourse(String courseCode, String courseName, String address, String phone, Integer holeCount,
			LocalDateTime now) {
		this.courseCode = courseCode;
		this.courseName = courseName;
		this.address = address;
		this.phone = phone;
		this.holeCount = holeCount;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String courseCode, String courseName, String address, String phone, Integer holeCount,
			LocalDateTime now) {
		this.courseCode = courseCode;
		this.courseName = courseName;
		this.address = address;
		this.phone = phone;
		this.holeCount = holeCount;
		this.updatedAt = now;
	}

	public void changeStatus(String courseStatus, boolean active, LocalDateTime now) {
		this.courseStatus = courseStatus;
		this.active = active;
		this.updatedAt = now;
	}

	/** 예약/티타임 생성이 가능한 상태 */
	public boolean isOperating() {
		return active && OPEN.equals(courseStatus);
	}

	public Long getGolfCourseId() {
		return golfCourseId;
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

	public String getCourseStatus() {
		return courseStatus;
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
