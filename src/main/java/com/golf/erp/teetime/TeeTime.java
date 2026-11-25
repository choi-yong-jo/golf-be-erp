package com.golf.erp.teetime;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * golf.tee_time. greenFee 는 1인 그린피이며 예약 앱은 이 값이 없으면 예약을 받지 않는다.
 * version 은 예약 앱과 공유하는 낙관적 락이다.
 */
@Entity
@Table(name = "tee_time")
public class TeeTime {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tee_time_id")
	private Long teeTimeId;
	@Column(name = "golf_course_id")
	private Long golfCourseId;
	@Column(length = 20)
	private String course;
	@Column(name = "play_date", nullable = false)
	private LocalDate playDate;
	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;
	@Column(name = "hole_type", nullable = false, length = 10)
	private String holeType;
	@Column(nullable = false)
	private Short capacity;
	@Column(name = "green_fee", precision = 12, scale = 2)
	private BigDecimal greenFee;
	@Enumerated(EnumType.STRING)
	@Column(name = "tee_time_status", nullable = false, length = 20)
	private TeeTimeStatus teeTimeStatus = TeeTimeStatus.OPEN;
	@Version
	@Column(nullable = false)
	private Long version;

	protected TeeTime() {
	}

	public TeeTime(Long golfCourseId, String course, LocalDate playDate, LocalTime startTime, String holeType,
			short capacity, BigDecimal greenFee) {
		this.golfCourseId = golfCourseId;
		this.course = course;
		this.playDate = playDate;
		this.startTime = startTime;
		this.holeType = holeType;
		this.capacity = capacity;
		this.greenFee = greenFee;
	}

	public LocalDateTime playAt() {
		return playDate.atTime(startTime);
	}

	public void book() {
		this.teeTimeStatus = TeeTimeStatus.BOOKED;
	}

	/** 예약 취소 시 BOOKED 였던 경우에만 다시 OPEN */
	public void release() {
		if (teeTimeStatus == TeeTimeStatus.BOOKED)
			this.teeTimeStatus = TeeTimeStatus.OPEN;
	}

	public void changeStatus(TeeTimeStatus status) {
		this.teeTimeStatus = status;
	}

	public void changeCapacity(short capacity) {
		this.capacity = capacity;
	}

	public void changeGreenFee(BigDecimal greenFee) {
		this.greenFee = greenFee;
	}

	public void changeHoleType(String holeType) {
		this.holeType = holeType;
	}

	public Long getTeeTimeId() {
		return teeTimeId;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public String getCourse() {
		return course;
	}

	public LocalDate getPlayDate() {
		return playDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public String getHoleType() {
		return holeType;
	}

	public Short getCapacity() {
		return capacity;
	}

	public BigDecimal getGreenFee() {
		return greenFee;
	}

	public TeeTimeStatus getTeeTimeStatus() {
		return teeTimeStatus;
	}

	public Long getVersion() {
		return version;
	}
}
