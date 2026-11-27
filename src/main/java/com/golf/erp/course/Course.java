package com.golf.erp.course;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.course. 골프장 안의 9홀 단위 코스(OUT, IN, EAST ...).
 * course_code 는 tee_time.course 에 그대로 들어가는 값이라 대문자로 정규화해 저장한다.
 * is_active 는 ALTER 로 추가된 컬럼(비활성 = 삭제 대신).
 */
@Entity
@Table(name = "course")
public class Course {
	public static final short DEFAULT_HOLE_COUNT = 9;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "course_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "course_code", nullable = false, length = 20)
	private String courseCode;
	@Column(name = "course_name", nullable = false, length = 100)
	private String courseName;
	@Column(name = "hole_count", nullable = false)
	private Short holeCount = DEFAULT_HOLE_COUNT;
	@Column(name = "sort_order", nullable = false)
	private Short sortOrder = 0;
	@Enumerated(EnumType.STRING)
	@Column(name = "course_status", nullable = false, length = 20)
	private CourseStatus status = CourseStatus.OPEN;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Course() {
	}

	public Course(Long golfCourseId, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String courseCode, String courseName, short holeCount, short sortOrder, LocalDateTime now) {
		this.courseCode = courseCode;
		this.courseName = courseName;
		this.holeCount = holeCount;
		this.sortOrder = sortOrder;
		this.updatedAt = now;
	}

	public void changeStatus(CourseStatus status, LocalDateTime now) {
		this.status = status;
		this.updatedAt = now;
	}

	public void changeActive(boolean active, LocalDateTime now) {
		this.active = active;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
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

	public Short getHoleCount() {
		return holeCount;
	}

	public Short getSortOrder() {
		return sortOrder;
	}

	public CourseStatus getStatus() {
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
