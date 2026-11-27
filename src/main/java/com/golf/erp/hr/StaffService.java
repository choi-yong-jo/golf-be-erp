package com.golf.erp.hr;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.hr.HrDtos.StaffRequest;
import com.golf.erp.hr.HrDtos.StaffResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class StaffService {
	private final StaffRepository repo;
	private final StaffShiftRepository shifts;
	private final PayrollRepository payrolls;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public StaffService(StaffRepository repo, StaffShiftRepository shifts, PayrollRepository payrolls, RefChecker refs,
			AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.shifts = shifts;
		this.payrolls = payrolls;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 이름·사번·연락처·이메일 */
	@Transactional(readOnly = true)
	public PageResult<StaffResponse> list(String keyword, String department, StaffStatus status, int page, int size) {
		Specification<Staff> spec = Specification.allOf(Specs.like(keyword, "name", "staffNo", "phone", "email"),
				Specs.eq("department", department == null ? null : department.trim()), Specs.eq("status", status));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by("staffNo"))), StaffResponse::of);
	}

	@Transactional(readOnly = true)
	public StaffResponse get(long id) {
		return StaffResponse.of(find(id));
	}

	public StaffResponse create(StaffRequest r) {
		String no = r.staffNo().trim();
		if (repo.existsByStaffNo(no))
			throw new ConflictException("이미 있는 사번입니다: " + no);
		if (r.adminUserId() != null) {
			refs.require(Ref.ADMIN_USER, r.adminUserId());
			if (repo.existsByAdminUserId(r.adminUserId()))
				throw new ConflictException("이미 다른 직원에 연결된 로그인 계정입니다: " + r.adminUserId());
		}
		LocalDateTime now = now();
		Staff s = new Staff(now);
		apply(s, no, r, now);
		return StaffResponse.of(repo.save(s));
	}

	public StaffResponse update(long id, StaffRequest r) {
		Staff s = find(id);
		String no = r.staffNo().trim();
		if (repo.existsByStaffNoAndIdNot(no, id))
			throw new ConflictException("이미 있는 사번입니다: " + no);
		if (r.adminUserId() != null && !r.adminUserId().equals(s.getAdminUserId())) {
			refs.require(Ref.ADMIN_USER, r.adminUserId());
			if (repo.existsByAdminUserIdAndIdNot(r.adminUserId(), id))
				throw new ConflictException("이미 다른 직원에 연결된 로그인 계정입니다: " + r.adminUserId());
		}
		apply(s, no, r, now());
		return StaffResponse.of(s);
	}

	/** 퇴직(RESIGNED) 처리 전에 오늘 이후 예정된 근무가 있으면 409(먼저 취소). */
	public StaffResponse changeStatus(long id, StaffStatus status) {
		Staff s = find(id);
		StaffStatus before = s.getStatus();
		if (before == status)
			return StaffResponse.of(s);
		if (status == StaffStatus.RESIGNED) {
			long pending = shifts.countByStaffIdAndStatusAndWorkDateGreaterThanEqual(id, ShiftStatus.SCHEDULED,
					LocalDate.now(clock));
			if (pending > 0)
				throw new ConflictException("오늘 이후 예정된 근무가 " + pending + "건 있습니다. 근무 일정을 먼저 취소하세요.");
		}
		s.changeStatus(status, now());
		audit.log("STATUS_CHANGE", "staff", id, Map.of("before", before, "after", status));
		return StaffResponse.of(s);
	}

	/**
	 * 잘못 등록한 직원만 삭제할 수 있다. 근무 일정·급여 이력이 있으면 409(퇴직 처리로 대신).
	 */
	public void delete(long id) {
		Staff s = find(id);
		if (shifts.existsByStaffId(id) || payrolls.existsByStaffId(id))
			throw new ConflictException("근무 일정 또는 급여 이력이 있는 직원은 삭제할 수 없습니다. 퇴직(RESIGNED) 처리하세요.");
		Map<String, Object> details = new HashMap<>();
		details.put("staffNo", s.getStaffNo());
		details.put("name", s.getName());
		repo.delete(s);
		audit.log("DELETE", "staff", id, details);
	}

	private void apply(Staff s, String staffNo, StaffRequest r, LocalDateTime now) {
		s.update(staffNo, r.name().trim(), r.adminUserId(), blankToNull(r.department()),
				blankToNull(r.positionName()), blankToNull(r.phone()), blankToNull(r.email()), r.hireDate(), now);
	}

	private Staff find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("직원", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
