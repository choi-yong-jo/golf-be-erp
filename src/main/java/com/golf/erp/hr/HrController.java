package com.golf.erp.hr;

import com.golf.erp.common.PageResult;
import com.golf.erp.hr.HrDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "인사·급여")
@RestController
@RequestMapping("/api")
public class HrController {
	private final StaffService staff;
	private final StaffShiftService shifts;
	private final PayrollService payrolls;

	public HrController(StaffService staff, StaffShiftService shifts, PayrollService payrolls) {
		this.staff = staff;
		this.shifts = shifts;
		this.payrolls = payrolls;
	}

	// ---- 직원 ----

	@Operation(summary = "직원 목록", description = "keyword: 이름·사번·연락처·이메일, department: 부서(정확히 일치)")
	@GetMapping("/staff")
	public PageResult<StaffResponse> staffList(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String department, @RequestParam(required = false) StaffStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return staff.list(keyword, department, status, page, size);
	}

	@GetMapping("/staff/{id}")
	public StaffResponse staff(@PathVariable long id) {
		return staff.get(id);
	}

	@Operation(summary = "직원 등록", description = "사번 중복·로그인 계정 중복 연결이면 409")
	@PostMapping("/staff")
	@ResponseStatus(HttpStatus.CREATED)
	public StaffResponse createStaff(@Valid @RequestBody StaffRequest r) {
		return staff.create(r);
	}

	@PutMapping("/staff/{id}")
	public StaffResponse updateStaff(@PathVariable long id, @Valid @RequestBody StaffRequest r) {
		return staff.update(id, r);
	}

	@Operation(summary = "직원 재직 상태 변경", description = "ACTIVE, ON_LEAVE, RESIGNED. 오늘 이후 예정 근무가 남아 있으면 퇴직 처리 불가(409)")
	@PatchMapping("/staff/{id}/status")
	public StaffResponse changeStaffStatus(@PathVariable long id, @Valid @RequestBody StaffStatusRequest r) {
		return staff.changeStatus(id, r.status());
	}

	@Operation(summary = "직원 삭제", description = "잘못 등록한 직원만. 근무·급여 이력이 있으면 409(퇴직 처리 사용)")
	@DeleteMapping("/staff/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteStaff(@PathVariable long id) {
		staff.delete(id);
	}

	// ---- 근무 일정 ----

	@Operation(summary = "근무 일정 목록", description = "from/to: 근무일 범위(포함)")
	@GetMapping("/staff-shifts")
	public PageResult<ShiftResponse> shiftList(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long staffId, @RequestParam(required = false) ShiftStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return shifts.list(from, to, staffId, status, page, size);
	}

	@GetMapping("/staff-shifts/{id}")
	public ShiftResponse shift(@PathVariable long id) {
		return shifts.get(id);
	}

	@Operation(summary = "근무 일정 등록", description = "종료 < 시작이면 야간 근무(다음 날 종료). 퇴직 직원·시간 겹침이면 409")
	@PostMapping("/staff-shifts")
	@ResponseStatus(HttpStatus.CREATED)
	public ShiftResponse createShift(@Valid @RequestBody ShiftCreateRequest r) {
		return shifts.create(r);
	}

	@Operation(summary = "근무 일정 수정", description = "출근 전인 예정(SCHEDULED) 근무만")
	@PutMapping("/staff-shifts/{id}")
	public ShiftResponse updateShift(@PathVariable long id, @Valid @RequestBody ShiftUpdateRequest r) {
		return shifts.update(id, r);
	}

	@Operation(summary = "출근", description = "at 을 비우면 현재 시각")
	@PostMapping("/staff-shifts/{id}/clock-in")
	public ShiftResponse clockIn(@PathVariable long id, @RequestBody(required = false) ClockRequest r) {
		return shifts.clockIn(id, r == null ? null : r.at());
	}

	@Operation(summary = "퇴근", description = "at 을 비우면 현재 시각. 근무 완료(WORKED)로 바뀐다")
	@PostMapping("/staff-shifts/{id}/clock-out")
	public ShiftResponse clockOut(@PathVariable long id, @RequestBody(required = false) ClockRequest r) {
		return shifts.clockOut(id, r == null ? null : r.at());
	}

	@Operation(summary = "결근 처리", description = "출근 전인 예정 근무만")
	@PostMapping("/staff-shifts/{id}/absent")
	public ShiftResponse markAbsent(@PathVariable long id) {
		return shifts.markAbsent(id);
	}

	@Operation(summary = "근무 일정 취소", description = "출근 전인 예정 근무만")
	@PostMapping("/staff-shifts/{id}/cancel")
	public ShiftResponse cancelShift(@PathVariable long id) {
		return shifts.cancel(id);
	}

	// ---- 급여 ----

	@Operation(summary = "급여 목록", description = "period: 급여 월(yyyy-MM)")
	@GetMapping("/payrolls")
	public PageResult<PayrollResponse> payrollList(@RequestParam(required = false) String period,
			@RequestParam(required = false) Long staffId, @RequestParam(required = false) PayrollStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return payrolls.list(period, staffId, status, page, size);
	}

	@GetMapping("/payrolls/{id}")
	public PayrollResponse payroll(@PathVariable long id) {
		return payrolls.get(id);
	}

	@Operation(summary = "급여 등록", description = "payPeriod 는 해당 월 1일로 저장. 같은 직원·같은 월이 이미 있으면 409")
	@PostMapping("/payrolls")
	@ResponseStatus(HttpStatus.CREATED)
	public PayrollResponse createPayroll(@Valid @RequestBody PayrollCreateRequest r) {
		return payrolls.create(r);
	}

	@Operation(summary = "월 급여 일괄 생성", description = "period(yyyy-MM) 급여가 없는 재직(ACTIVE) 직원 모두에게 DRAFT 생성."
			+ " 기본급은 직전 급여의 기본급을 이어받고(없으면 0), 수당·공제는 0")
	@PostMapping("/payrolls/generate")
	@ResponseStatus(HttpStatus.CREATED)
	public PayrollGenerateResponse generatePayrolls(@RequestParam String period) {
		return payrolls.generate(period);
	}

	@Operation(summary = "급여 금액 수정", description = "DRAFT 만. 실수령액 = 기본급 + 초과근무수당 - 공제액(음수 불가)")
	@PutMapping("/payrolls/{id}")
	public PayrollResponse updatePayroll(@PathVariable long id, @Valid @RequestBody PayrollUpdateRequest r) {
		return payrolls.update(id, r);
	}

	@Operation(summary = "급여 승인", description = "DRAFT → APPROVED")
	@PostMapping("/payrolls/{id}/approve")
	public PayrollResponse approvePayroll(@PathVariable long id) {
		return payrolls.approve(id);
	}

	@Operation(summary = "급여 지급", description = "APPROVED → PAID")
	@PostMapping("/payrolls/{id}/pay")
	public PayrollResponse payPayroll(@PathVariable long id) {
		return payrolls.pay(id);
	}

	@Operation(summary = "급여 삭제", description = "DRAFT 만")
	@DeleteMapping("/payrolls/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deletePayroll(@PathVariable long id) {
		payrolls.delete(id);
	}
}
