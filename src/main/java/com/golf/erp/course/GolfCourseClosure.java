package com.golf.erp.course;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** golf.golf_course_closure. [closureStart, closureEnd) 구간은 예약/티타임 생성을 막는다. */
@Entity
@Table(name = "golf_course_closure")
public class GolfCourseClosure {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "closure_id")
	private Long closureId;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "closure_start", nullable = false)
	private LocalDateTime closureStart;
	@Column(name = "closure_end", nullable = false)
	private LocalDateTime closureEnd;
	@Column(name = "closure_type", nullable = false, length = 20)
	private String closureType;
	@Column(nullable = false, length = 500)
	private String reason;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected GolfCourseClosure() {
	}

	public GolfCourseClosure(Long golfCourseId, LocalDateTime closureStart, LocalDateTime closureEnd,
			String closureType, String reason) {
		this.golfCourseId = golfCourseId;
		this.closureStart = closureStart;
		this.closureEnd = closureEnd;
		this.closureType = closureType;
		this.reason = reason;
	}

	public void deactivate() {
		this.active = false;
	}

	public Long getClosureId() {
		return closureId;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public LocalDateTime getClosureStart() {
		return closureStart;
	}

	public LocalDateTime getClosureEnd() {
		return closureEnd;
	}

	public String getClosureType() {
		return closureType;
	}

	public String getReason() {
		return reason;
	}

	public boolean isActive() {
		return active;
	}
}
