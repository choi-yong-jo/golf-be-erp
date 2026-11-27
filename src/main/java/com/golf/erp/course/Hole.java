package com.golf.erp.course;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.hole. 코스 안 홀 번호(1~코스 홀 수)별 파·핸디캡·티별 거리(m). */
@Entity
@Table(name = "hole")
public class Hole {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "hole_id")
	private Long id;
	@Column(name = "course_id", nullable = false, updatable = false)
	private Long courseId;
	@Column(name = "hole_no", nullable = false, updatable = false)
	private Short holeNo;
	@Column(name = "par", nullable = false)
	private Short par;
	@Column(name = "handicap")
	private Short handicap;
	@Column(name = "distance_back")
	private Integer distanceBack;
	@Column(name = "distance_regular")
	private Integer distanceRegular;
	@Column(name = "distance_front")
	private Integer distanceFront;
	@Column(name = "memo", length = 500)
	private String memo;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Hole() {
	}

	public Hole(Long courseId, short holeNo, LocalDateTime now) {
		this.courseId = courseId;
		this.holeNo = holeNo;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(short par, Short handicap, Integer distanceBack, Integer distanceRegular,
			Integer distanceFront, String memo, LocalDateTime now) {
		this.par = par;
		this.handicap = handicap;
		this.distanceBack = distanceBack;
		this.distanceRegular = distanceRegular;
		this.distanceFront = distanceFront;
		this.memo = memo;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getCourseId() {
		return courseId;
	}

	public Short getHoleNo() {
		return holeNo;
	}

	public Short getPar() {
		return par;
	}

	public Short getHandicap() {
		return handicap;
	}

	public Integer getDistanceBack() {
		return distanceBack;
	}

	public Integer getDistanceRegular() {
		return distanceRegular;
	}

	public Integer getDistanceFront() {
		return distanceFront;
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
