package com.golf.erp.round;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * golf.round — 한 팀이 체크인부터 종료까지 골프장을 이용한 한 번.
 * JPQL 의 round() 함수와 헷갈리지 않도록 엔티티(클래스) 이름은 GolfRound 로 둔다(테이블은 golf.round).
 * 상태 전이: CHECKED_IN → PLAYING → FINISHED, CHECKED_IN·PLAYING → CANCELLED.
 */
@Entity
@Table(name = "round")
public class GolfRound {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_id")
	private Long id;
	/** RD-yyyyMMdd-{id}. id 가 생긴 뒤(saveAndFlush) 채운다(컬럼이 NULL 허용이라 임시 번호를 쓰지 않는다). */
	@Column(name = "round_no", length = 30)
	private String roundNo;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "reservation_id")
	private Long reservationId;
	@Column(name = "tee_time_id")
	private Long teeTimeId;
	@Column(name = "play_date", nullable = false)
	private LocalDate playDate;
	@Column(name = "tee_off_time")
	private LocalTime teeOffTime;
	@Column(name = "planned_holes", nullable = false)
	private Short plannedHoles;
	@Enumerated(EnumType.STRING)
	@Column(name = "round_status", nullable = false, length = 20)
	private RoundStatus status = RoundStatus.CHECKED_IN;
	@Column(name = "current_course_id")
	private Long currentCourseId;
	@Column(name = "checked_in_at", nullable = false)
	private LocalDateTime checkedInAt;
	@Column(name = "started_at")
	private LocalDateTime startedAt;
	@Column(name = "finished_at")
	private LocalDateTime finishedAt;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;
	@Enumerated(EnumType.STRING)
	@Column(name = "settlement_status", nullable = false, length = 20)
	private RoundSettlementStatus settlementStatus = RoundSettlementStatus.UNPAID;
	@Column(length = 500)
	private String memo;
	@Column(name = "created_by")
	private Long createdBy;
	@Version
	@Column(name = "version", nullable = false)
	private Long version;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected GolfRound() {
	}

	public GolfRound(Long golfCourseId, Long reservationId, Long teeTimeId, LocalDate playDate, LocalTime teeOffTime,
			short plannedHoles, String memo, Long createdBy, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.reservationId = reservationId;
		this.teeTimeId = teeTimeId;
		this.playDate = playDate;
		this.teeOffTime = teeOffTime;
		this.plannedHoles = plannedHoles;
		this.memo = memo;
		this.createdBy = createdBy;
		this.checkedInAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void assignRoundNo(String roundNo) {
		this.roundNo = roundNo;
	}

	/** 출발: 첫 코스 사용 시작 */
	void start(long courseId, LocalDateTime now) {
		if (status != RoundStatus.CHECKED_IN)
			throw new ConflictException("체크인 상태의 라운드만 출발할 수 있습니다(현재 " + status + ").");
		this.status = RoundStatus.PLAYING;
		this.currentCourseId = courseId;
		this.startedAt = now;
		this.updatedAt = now;
	}

	void moveTo(long courseId, LocalDateTime now) {
		requirePlaying();
		this.currentCourseId = courseId;
		this.updatedAt = now;
	}

	void finish(LocalDateTime now) {
		requirePlaying();
		this.status = RoundStatus.FINISHED;
		this.currentCourseId = null;
		this.finishedAt = now;
		this.updatedAt = now;
	}

	void cancel(String reason, LocalDateTime now) {
		requireActive();
		this.status = RoundStatus.CANCELLED;
		this.currentCourseId = null;
		this.cancelledAt = now;
		this.cancelReason = reason;
		this.updatedAt = now;
	}

	public void changeSettlementStatus(RoundSettlementStatus settlementStatus, LocalDateTime now) {
		this.settlementStatus = settlementStatus;
		this.updatedAt = now;
	}

	/** 하위 데이터(참가자·배정·요금 등)가 바뀌었음을 라운드에 남긴다(updated_at, version 증가). */
	public void touch(LocalDateTime now) {
		this.updatedAt = now;
	}

	public void requirePlaying() {
		if (status != RoundStatus.PLAYING)
			throw new ConflictException("플레이 중인 라운드가 아닙니다(현재 " + status + ").");
	}

	/** 체크인·플레이 중인 라운드만 */
	public void requireActive() {
		if (!status.isActive())
			throw new ConflictException("이미 종료되었거나 취소된 라운드입니다(현재 " + status + ").");
	}

	public Long getId() {
		return id;
	}

	public String getRoundNo() {
		return roundNo;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public Long getTeeTimeId() {
		return teeTimeId;
	}

	public LocalDate getPlayDate() {
		return playDate;
	}

	public LocalTime getTeeOffTime() {
		return teeOffTime;
	}

	public Short getPlannedHoles() {
		return plannedHoles;
	}

	public RoundStatus getStatus() {
		return status;
	}

	public Long getCurrentCourseId() {
		return currentCourseId;
	}

	public LocalDateTime getCheckedInAt() {
		return checkedInAt;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getFinishedAt() {
		return finishedAt;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public RoundSettlementStatus getSettlementStatus() {
		return settlementStatus;
	}

	public String getMemo() {
		return memo;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getVersion() {
		return version;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
