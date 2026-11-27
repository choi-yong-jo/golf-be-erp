package com.golf.erp.hr;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 인사(직원·근무 일정·급여) API 요청/응답 */
public final class HrDtos {
	/** NUMERIC(14,2) 상한 */
	static final String MAX_AMOUNT = "999999999999.99";

	private HrDtos() {
	}

	// ---- 직원 ----

	/** 등록·수정 공통. adminUserId: 연결할 ERP 로그인 계정(선택, 한 계정은 한 직원에만) */
	public record StaffRequest(@NotBlank @Size(max = 30) String staffNo, @NotBlank @Size(max = 100) String name,
			Long adminUserId, @Size(max = 100) String department, @Size(max = 100) String positionName,
			@Size(max = 30) String phone, @Email @Size(max = 255) String email, LocalDate hireDate) {
	}

	public record StaffStatusRequest(@NotNull StaffStatus status) {
	}

	public record StaffResponse(Long id, String staffNo, String name, Long adminUserId, String department,
			String positionName, String phone, String email, LocalDate hireDate, StaffStatus status,
			LocalDateTime createdAt, LocalDateTime updatedAt) {

		static StaffResponse of(Staff s) {
			return new StaffResponse(s.getId(), s.getStaffNo(), s.getName(), s.getAdminUserId(), s.getDepartment(),
					s.getPositionName(), s.getPhone(), s.getEmail(), s.getHireDate(), s.getStatus(), s.getCreatedAt(),
					s.getUpdatedAt());
		}
	}

	// ---- 근무 일정 ----

	/** scheduledEnd 가 scheduledStart 보다 이르면 다음 날 끝나는 야간 근무 */
	public record ShiftCreateRequest(@NotNull Long staffId, @NotNull LocalDate workDate,
			@NotNull LocalTime scheduledStart, @NotNull LocalTime scheduledEnd, @Size(max = 500) String memo) {
	}

	public record ShiftUpdateRequest(@NotNull LocalDate workDate, @NotNull LocalTime scheduledStart,
			@NotNull LocalTime scheduledEnd, @Size(max = 500) String memo) {
	}

	/** 출근·퇴근 시각. 비우면 현재 시각 */
	public record ClockRequest(LocalDateTime at) {
	}

	public record ShiftResponse(Long id, Long staffId, String staffNo, String staffName, LocalDate workDate,
			LocalTime scheduledStart, LocalTime scheduledEnd, boolean overnight, LocalDateTime actualStart,
			LocalDateTime actualEnd, ShiftStatus status, String memo) {

		static ShiftResponse of(StaffShift s, Staff staff) {
			return new ShiftResponse(s.getId(), s.getStaffId(), staff == null ? null : staff.getStaffNo(),
					staff == null ? null : staff.getName(), s.getWorkDate(), s.getScheduledStart(),
					s.getScheduledEnd(), !s.getScheduledEnd().isAfter(s.getScheduledStart()), s.getActualStart(),
					s.getActualEnd(), s.getStatus(), s.getMemo());
		}
	}

	// ---- 급여 ----

	/** payPeriod: 해당 월의 아무 날짜(1일로 정규화). overtime/deduction 은 비우면 0 */
	public record PayrollCreateRequest(@NotNull Long staffId, @NotNull LocalDate payPeriod,
			@NotNull @PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal baseAmount,
			@PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal overtimeAmount,
			@PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal deductionAmount, @Size(max = 500) String memo) {
	}

	public record PayrollUpdateRequest(@NotNull @PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal baseAmount,
			@PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal overtimeAmount,
			@PositiveOrZero @DecimalMax(MAX_AMOUNT) BigDecimal deductionAmount, @Size(max = 500) String memo) {
	}

	public record PayrollResponse(Long id, Long staffId, String staffNo, String staffName, LocalDate payPeriod,
			BigDecimal baseAmount, BigDecimal overtimeAmount, BigDecimal deductionAmount, BigDecimal netAmount,
			PayrollStatus status, LocalDateTime paidAt, String memo) {

		static PayrollResponse of(Payroll p, Staff staff) {
			return new PayrollResponse(p.getId(), p.getStaffId(), staff == null ? null : staff.getStaffNo(),
					staff == null ? null : staff.getName(), p.getPayPeriod(), p.getBaseAmount(),
					p.getOvertimeAmount(), p.getDeductionAmount(), p.getNetAmount(), p.getStatus(), p.getPaidAt(),
					p.getMemo());
		}
	}

	/** 월 급여 일괄 생성 결과 */
	public record PayrollGenerateResponse(LocalDate payPeriod, int createdCount, int skippedCount,
			List<PayrollResponse> created) {
	}
}
