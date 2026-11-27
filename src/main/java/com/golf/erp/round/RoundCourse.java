package com.golf.erp.round;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.round_course — 라운드의 코스 사용 구간. finished_at 이 NULL 인 행이 지금 사용 중인 코스(라운드당 하나). */
@Entity
@Table(name = "round_course")
public class RoundCourse {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_course_id")
	private Long id;
	@Column(name = "round_id", nullable = false)
	private Long roundId;
	@Column(name = "course_id", nullable = false)
	private Long courseId;
	@Column(name = "seq_no", nullable = false)
	private Short seqNo;
	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;
	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	protected RoundCourse() {
	}

	public RoundCourse(Long roundId, Long courseId, short seqNo, LocalDateTime now) {
		this.roundId = roundId;
		this.courseId = courseId;
		this.seqNo = seqNo;
		this.startedAt = now;
	}

	void finish(LocalDateTime now) {
		if (finishedAt == null)
			this.finishedAt = now;
	}

	public boolean isOpen() {
		return finishedAt == null;
	}

	public Long getId() {
		return id;
	}

	public Long getRoundId() {
		return roundId;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Short getSeqNo() {
		return seqNo;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}
}
