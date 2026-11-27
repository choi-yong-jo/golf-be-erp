package com.golf.erp.course;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.golf_course_closure. [closureStart, closureEnd) 구간(시작 포함, 종료 미포함)에는 티타임 생성·예약을 막는다.
 * 해제는 is_active=false 로 하고 행은 이력으로 남긴다. 테이블에 created_at/updated_at 이 없다.
 */
@Entity
@Table(name = "golf_course_closure")
public class GolfCourseClosure {
	public static final String DEFAULT_TYPE = "CLOSED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "closure_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "closure_start", nullable = false)
	private LocalDateTime closureStart;
	@Column(name = "closure_end", nullable = false)
	private LocalDateTime closureEnd;
	@Column(name = "closure_type", nullable = false, length = 20)
	private String closureType = DEFAULT_TYPE;
	@Column(name = "reason", nullable = false, length = 500)
	private String reason;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected GolfCourseClosure() {
	}

	public GolfCourseClosure(Long golfCourseId, LocalDateTime closureStart, LocalDateTime closureEnd,
			String closureType, String reason) {
		if (!closureEnd.isAfter(closureStart))
			throw new IllegalArgumentException("휴장 종료 시각은 시작 시각 이후여야 합니다.");
		this.golfCourseId = golfCourseId;
		this.closureStart = closureStart;
		this.closureEnd = closureEnd;
		this.closureType = closureType == null ? DEFAULT_TYPE : closureType;
		this.reason = reason;
	}

	public void deactivate() {
		if (!active)
			throw new ConflictException("이미 해제된 휴장입니다.");
		this.active = false;
	}

	public Long getId() {
		return id;
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
