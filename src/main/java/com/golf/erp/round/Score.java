package com.golf.erp.round;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.score — 한 참가자가 한 코스 사용 구간(round_course)의 한 홀에서 친 기록 */
@Entity
@Table(name = "score")
public class Score {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "score_id")
	private Long id;
	@Column(name = "round_player_id", nullable = false)
	private Long roundPlayerId;
	@Column(name = "round_course_id", nullable = false)
	private Long roundCourseId;
	@Column(name = "hole_id", nullable = false)
	private Long holeId;
	@Column(name = "strokes", nullable = false)
	private Short strokes;
	@Column(name = "putts")
	private Short putts;
	@Column(name = "penalties", nullable = false)
	private Short penalties = 0;
	@Column(name = "fairway_hit")
	private Boolean fairwayHit;
	@Column(name = "recorded_by")
	private Long recordedBy;
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private LocalDateTime recordedAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Score() {
	}

	public Score(Long roundPlayerId, Long roundCourseId, Long holeId, LocalDateTime now) {
		this.roundPlayerId = roundPlayerId;
		this.roundCourseId = roundCourseId;
		this.holeId = holeId;
		this.recordedAt = now;
	}

	void record(short strokes, Short putts, short penalties, Boolean fairwayHit, Long recordedBy, LocalDateTime now) {
		if (putts != null && putts > strokes)
			throw new IllegalArgumentException("퍼트 수가 타수보다 많을 수 없습니다.");
		this.strokes = strokes;
		this.putts = putts;
		this.penalties = penalties;
		this.fairwayHit = fairwayHit;
		this.recordedBy = recordedBy;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getRoundPlayerId() {
		return roundPlayerId;
	}

	public Long getRoundCourseId() {
		return roundCourseId;
	}

	public Long getHoleId() {
		return holeId;
	}

	public Short getStrokes() {
		return strokes;
	}

	public Short getPutts() {
		return putts;
	}

	public Short getPenalties() {
		return penalties;
	}

	public Boolean getFairwayHit() {
		return fairwayHit;
	}

	public Long getRecordedBy() {
		return recordedBy;
	}

	public LocalDateTime getRecordedAt() {
		return recordedAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
