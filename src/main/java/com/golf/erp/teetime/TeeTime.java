package com.golf.erp.teetime;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * golf.tee_time. 슬롯 = (골프장, 날짜, 시각, 코스) 이며 ux_tee_time_slot 식 인덱스가 유일성을 지킨다.
 * greenFee 는 1인 그린피이며 예약 앱은 이 값이 없으면 예약을 받지 않는다.
 * version 은 예약 앱·예약 모듈과 공유하는 낙관적 락이다(null 로 두면 저장 시 Hibernate 가 0 으로 채운다).
 * 테이블에 created_at/updated_at 이 없다.
 */
@Entity
@Table(name = "tee_time")
public class TeeTime {
	public static final String DEFAULT_HOLE_TYPE = "18H";
	public static final short DEFAULT_CAPACITY = 4;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tee_time_id")
	private Long id;
	@Column(name = "golf_course_id", updatable = false)
	private Long golfCourseId;
	@Column(name = "course", length = 20, updatable = false)
	private String course;
	@Column(name = "play_date", nullable = false, updatable = false)
	private LocalDate playDate;
	@Column(name = "start_time", nullable = false, updatable = false)
	private LocalTime startTime;
	@Column(name = "hole_type", nullable = false, length = 10)
	private String holeType = DEFAULT_HOLE_TYPE;
	@Column(name = "capacity", nullable = false)
	private Short capacity = DEFAULT_CAPACITY;
	@Column(name = "green_fee", precision = 12, scale = 2)
	private BigDecimal greenFee;
	@Enumerated(EnumType.STRING)
	@Column(name = "tee_time_status", nullable = false, length = 20)
	private TeeTimeStatus status = TeeTimeStatus.OPEN;
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	protected TeeTime() {
	}

	public TeeTime(Long golfCourseId, String course, LocalDate playDate, LocalTime startTime) {
		this.golfCourseId = golfCourseId;
		this.course = course;
		this.playDate = playDate;
		this.startTime = startTime;
	}

	public LocalDateTime playAt() {
		return playDate.atTime(startTime);
	}

	public void update(String holeType, short capacity, BigDecimal greenFee) {
		this.holeType = holeType;
		this.capacity = capacity;
		this.greenFee = greenFee;
	}

	/** 관리자 상태 변경(OPEN/BLOCKED/CLOSED). BOOKED 는 예약 모듈만 지정한다. */
	public void changeStatus(TeeTimeStatus status) {
		if (!status.isAdminSettable())
			throw new IllegalArgumentException("BOOKED 상태는 예약 생성으로만 바뀝니다.");
		if (this.status == status)
			throw new ConflictException("이미 " + status + " 상태입니다.");
		this.status = status;
	}

	public Long getId() {
		return id;
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

	public TeeTimeStatus getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}
}
