package com.golf.erp.hr;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * golf.staff_shift. 근무 일정 하나. scheduled_end 가 scheduled_start 보다 이르면 다음 날 끝나는 야간 근무로 본다.
 * SCHEDULED → (출근 actual_start) → 퇴근 actual_end 와 함께 WORKED. SCHEDULED 에서 ABSENT·CANCELLED 로 끝낼 수 있다.
 */
@Entity
@Table(name = "staff_shift")
public class StaffShift {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "staff_shift_id")
	private Long id;
	@Column(name = "staff_id", nullable = false)
	private Long staffId;
	@Column(name = "work_date", nullable = false)
	private LocalDate workDate;
	@Column(name = "scheduled_start", nullable = false)
	private LocalTime scheduledStart;
	@Column(name = "scheduled_end", nullable = false)
	private LocalTime scheduledEnd;
	@Column(name = "actual_start")
	private LocalDateTime actualStart;
	@Column(name = "actual_end")
	private LocalDateTime actualEnd;
	@Enumerated(EnumType.STRING)
	@Column(name = "shift_status", nullable = false, length = 20)
	private ShiftStatus status = ShiftStatus.SCHEDULED;
	@Column(name = "memo", length = 500)
	private String memo;

	protected StaffShift() {
	}

	public StaffShift(Long staffId) {
		this.staffId = staffId;
	}

	public void reschedule(LocalDate workDate, LocalTime scheduledStart, LocalTime scheduledEnd, String memo) {
		requireNotStarted("수정");
		this.workDate = workDate;
		this.scheduledStart = scheduledStart;
		this.scheduledEnd = scheduledEnd;
		this.memo = memo;
	}

	public void clockIn(LocalDateTime at) {
		requireNotStarted("출근 처리");
		this.actualStart = at;
	}

	public void clockOut(LocalDateTime at) {
		if (status != ShiftStatus.SCHEDULED)
			throw new ConflictException("예정(SCHEDULED) 상태의 근무만 퇴근 처리할 수 있습니다. 현재: " + status);
		if (actualStart == null)
			throw new ConflictException("출근 기록이 없는 근무입니다. 먼저 출근 처리하세요.");
		if (!at.isAfter(actualStart))
			throw new IllegalArgumentException("퇴근 시각은 출근 시각(" + actualStart + ") 이후여야 합니다.");
		this.actualEnd = at;
		this.status = ShiftStatus.WORKED;
	}

	public void markAbsent() {
		requireNotStarted("결근 처리");
		this.status = ShiftStatus.ABSENT;
	}

	public void cancel() {
		requireNotStarted("취소");
		this.status = ShiftStatus.CANCELLED;
	}

	private void requireNotStarted(String action) {
		if (status != ShiftStatus.SCHEDULED)
			throw new ConflictException("예정(SCHEDULED) 상태의 근무만 " + action + "할 수 있습니다. 현재: " + status);
		if (actualStart != null)
			throw new ConflictException("이미 출근 처리된 근무는 " + action + "할 수 없습니다.");
	}

	/** 예정 근무 시작 시각 */
	public LocalDateTime scheduledFrom() {
		return workDate.atTime(scheduledStart);
	}

	/** 예정 근무 종료 시각(야간 근무면 다음 날) */
	public LocalDateTime scheduledTo() {
		return scheduledEnd.isAfter(scheduledStart) ? workDate.atTime(scheduledEnd)
				: workDate.plusDays(1).atTime(scheduledEnd);
	}

	public Long getId() {
		return id;
	}

	public Long getStaffId() {
		return staffId;
	}

	public LocalDate getWorkDate() {
		return workDate;
	}

	public LocalTime getScheduledStart() {
		return scheduledStart;
	}

	public LocalTime getScheduledEnd() {
		return scheduledEnd;
	}

	public LocalDateTime getActualStart() {
		return actualStart;
	}

	public LocalDateTime getActualEnd() {
		return actualEnd;
	}

	public ShiftStatus getStatus() {
		return status;
	}

	public String getMemo() {
		return memo;
	}
}
