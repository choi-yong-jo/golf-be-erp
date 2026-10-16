package com.golf.erp.course;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "golf_courses")
public class GolfCourse {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "course_code", nullable = false, unique = true)
	private String courseCode;
	@Column(nullable = false)
	private String name;
	@Column(name = "hole_count", nullable = false)
	private int holeCount = 18;
	@Column(nullable = false)
	private String status = "OPEN";
	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	protected GolfCourse() {
	}

	public Long getId() {
		return id;
	}

	public String getCourseCode() {
		return courseCode;
	}

	public String getName() {
		return name;
	}

	public int getHoleCount() {
		return holeCount;
	}

	public String getStatus() {
		return status;
	}
}
