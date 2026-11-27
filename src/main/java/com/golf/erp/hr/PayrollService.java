package com.golf.erp.hr;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.hr.HrDtos.PayrollCreateRequest;
import com.golf.erp.hr.HrDtos.PayrollGenerateResponse;
import com.golf.erp.hr.HrDtos.PayrollResponse;
import com.golf.erp.hr.HrDtos.PayrollUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayrollService {
	private final PayrollRepository repo;
	private final StaffRepository staffRepo;
	private final AuditLogger audit;
	private final Clock clock;

	public PayrollService(PayrollRepository repo, StaffRepository staffRepo, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.staffRepo = staffRepo;
		this.audit = audit;
		this.clock = clock;
	}

	/** period: 급여 월(yyyy-MM 또는 yyyy-MM-dd) */
	@Transactional(readOnly = true)
	public PageResult<PayrollResponse> list(String period, Long staffId, PayrollStatus status, int page, int size) {
		LocalDate p = period == null || period.isBlank() ? null : parsePeriod(period);
		Specification<Payroll> spec = Specification.allOf(Specs.eq("payPeriod", p), Specs.eq("staffId", staffId),
				Specs.eq("status", status));
		Page<Payroll> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("payPeriod"), Sort.Order.asc("staffId"))));
		Map<Long, Staff> staff = staffMap(result.getContent());
		return PageResult.of(result, x -> PayrollResponse.of(x, staff.get(x.getStaffId())));
	}

	@Transactional(readOnly = true)
	public PayrollResponse get(long id) {
		return toResponse(find(id));
	}

	/** 같은 직원·같은 월 급여가 이미 있으면 409 */
	public PayrollResponse create(PayrollCreateRequest r) {
		Staff staff = staffRepo.findById(r.staffId()).orElseThrow(() -> NotFoundException.of("직원", r.staffId()));
		LocalDate period = r.payPeriod().withDayOfMonth(1);
		if (repo.existsByStaffIdAndPayPeriod(staff.getId(), period))
			throw new ConflictException(
					staff.getName() + "(" + staff.getStaffNo() + ")의 " + YearMonth.from(period) + " 급여가 이미 있습니다.");
		Payroll p = new Payroll(staff.getId(), period);
		p.changeAmounts(r.baseAmount(), r.overtimeAmount(), r.deductionAmount(), blankToNull(r.memo()));
		return PayrollResponse.of(repo.save(p), staff);
	}

	public PayrollResponse update(long id, PayrollUpdateRequest r) {
		Payroll p = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("급여", id));
		p.changeAmounts(r.baseAmount(), r.overtimeAmount(), r.deductionAmount(), blankToNull(r.memo()));
		return toResponse(p);
	}

	public PayrollResponse approve(long id) {
		Payroll p = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("급여", id));
		p.approve();
		audit.log("APPROVE", "payroll", id, Map.of("staffId", p.getStaffId(), "payPeriod", p.getPayPeriod().toString(),
				"netAmount", p.getNetAmount()));
		return toResponse(p);
	}

	public PayrollResponse pay(long id) {
		Payroll p = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("급여", id));
		p.pay(LocalDateTime.now(clock));
		audit.log("PAY", "payroll", id, Map.of("staffId", p.getStaffId(), "payPeriod", p.getPayPeriod().toString(),
				"netAmount", p.getNetAmount()));
		return toResponse(p);
	}

	/** 작성 중(DRAFT) 급여만 삭제할 수 있다. */
	public void delete(long id) {
		Payroll p = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("급여", id));
		if (p.getStatus() != PayrollStatus.DRAFT)
			throw new ConflictException("작성 중(DRAFT)인 급여만 삭제할 수 있습니다. 현재: " + p.getStatus());
		repo.delete(p);
		audit.log("DELETE", "payroll", id, Map.of("staffId", p.getStaffId(), "payPeriod", p.getPayPeriod().toString()));
	}

	/**
	 * 해당 월 급여가 없는 재직(ACTIVE) 직원 모두에게 DRAFT 급여를 만든다.
	 * 기본급은 그 직원의 직전 급여(해당 월 이전 가장 최근 월)의 기본급을 이어받고, 이전 급여가 없으면 0 이다.
	 * 초과근무수당·공제액은 0 으로 시작한다(월마다 달라지므로 DRAFT 에서 입력).
	 */
	public PayrollGenerateResponse generate(String period) {
		if (period == null || period.isBlank())
			throw new IllegalArgumentException("급여 월(period)을 입력하세요. 예: 2026-11");
		LocalDate p = parsePeriod(period);
		Set<Long> existing = new HashSet<>(repo.findStaffIdsByPayPeriod(p));
		Map<Long, BigDecimal> lastBase = repo.findLatestBefore(p).stream()
				.collect(Collectors.toMap(Payroll::getStaffId, Payroll::getBaseAmount, (a, b) -> a));
		List<Staff> active = staffRepo.findByStatusOrderByStaffNo(StaffStatus.ACTIVE);
		List<PayrollResponse> created = new ArrayList<>();
		int skipped = 0;
		for (Staff s : active) {
			if (existing.contains(s.getId())) {
				skipped++;
				continue;
			}
			Payroll x = new Payroll(s.getId(), p);
			x.changeAmounts(lastBase.getOrDefault(s.getId(), BigDecimal.ZERO), BigDecimal.ZERO, BigDecimal.ZERO,
					null);
			created.add(PayrollResponse.of(repo.save(x), s));
		}
		if (!created.isEmpty())
			audit.log("GENERATE", "payroll", null, Map.of("payPeriod", p.toString(), "created", created.size()));
		return new PayrollGenerateResponse(p, created.size(), skipped, created);
	}

	/** yyyy-MM 또는 yyyy-MM-dd → 해당 월 1일 */
	static LocalDate parsePeriod(String period) {
		String s = period.trim();
		try {
			if (s.length() == 7)
				return YearMonth.parse(s).atDay(1);
			return LocalDate.parse(s).withDayOfMonth(1);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException("급여 월 형식이 올바르지 않습니다(yyyy-MM): " + period);
		}
	}

	private Map<Long, Staff> staffMap(List<Payroll> rows) {
		return staffRepo.findAllById(rows.stream().map(Payroll::getStaffId).distinct().toList()).stream()
				.collect(Collectors.toMap(Staff::getId, Function.identity()));
	}

	private PayrollResponse toResponse(Payroll p) {
		return PayrollResponse.of(p, staffRepo.findById(p.getStaffId()).orElse(null));
	}

	private Payroll find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("급여", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
