package com.golf.erp.hr;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.hr.HrDtos.ShiftCreateRequest;
import com.golf.erp.hr.HrDtos.ShiftResponse;
import com.golf.erp.hr.HrDtos.ShiftUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class StaffShiftService {
	/** 겹침 검사 대상: 실제로 근무 시간을 차지하는 일정 */
	private static final Set<ShiftStatus> OCCUPYING = EnumSet.of(ShiftStatus.SCHEDULED, ShiftStatus.WORKED);

	private final StaffShiftRepository repo;
	private final StaffRepository staffRepo;
	private final Clock clock;

	public StaffShiftService(StaffShiftRepository repo, StaffRepository staffRepo, Clock clock) {
		this.repo = repo;
		this.staffRepo = staffRepo;
		this.clock = clock;
	}

	/** from/to: 근무일(work_date) 범위(양 끝 포함) */
	@Transactional(readOnly = true)
	public PageResult<ShiftResponse> list(LocalDate from, LocalDate to, Long staffId, ShiftStatus status, int page,
			int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		Specification<StaffShift> spec = Specification.allOf(Specs.gte("workDate", from), Specs.lte("workDate", to),
				Specs.eq("staffId", staffId), Specs.eq("status", status));
		Page<StaffShift> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by("workDate", "scheduledStart", "staffId")));
		Map<Long, Staff> staff = staffRepo
				.findAllById(result.getContent().stream().map(StaffShift::getStaffId).distinct().toList()).stream()
				.collect(Collectors.toMap(Staff::getId, Function.identity()));
		return PageResult.of(result, s -> ShiftResponse.of(s, staff.get(s.getStaffId())));
	}

	@Transactional(readOnly = true)
	public ShiftResponse get(long id) {
		return toResponse(find(id));
	}

	/** 퇴직 직원은 등록할 수 없고, 같은 직원의 다른 근무와 시간이 겹치면 409. */
	public ShiftResponse create(ShiftCreateRequest r) {
		Staff staff = staffRepo.findByIdForUpdate(r.staffId())
				.orElseThrow(() -> NotFoundException.of("직원", r.staffId()));
		if (staff.getStatus() == StaffStatus.RESIGNED)
			throw new ConflictException("퇴직한 직원에게는 근무 일정을 등록할 수 없습니다: " + staff.getStaffNo());
		validateTimes(r.scheduledStart(), r.scheduledEnd());
		StaffShift s = new StaffShift(staff.getId());
		s.reschedule(r.workDate(), r.scheduledStart(), r.scheduledEnd(), blankToNull(r.memo()));
		requireNoOverlap(s);
		return ShiftResponse.of(repo.save(s), staff);
	}

	/** 출근 전인 예정 근무만 수정할 수 있다. */
	public ShiftResponse update(long id, ShiftUpdateRequest r) {
		StaffShift s = find(id);
		Staff staff = staffRepo.findByIdForUpdate(s.getStaffId())
				.orElseThrow(() -> NotFoundException.of("직원", s.getStaffId()));
		validateTimes(r.scheduledStart(), r.scheduledEnd());
		s.reschedule(r.workDate(), r.scheduledStart(), r.scheduledEnd(), blankToNull(r.memo()));
		requireNoOverlap(s);
		return ShiftResponse.of(s, staff);
	}

	public ShiftResponse clockIn(long id, LocalDateTime at) {
		StaffShift s = find(id);
		s.clockIn(at == null ? now() : at);
		return toResponse(s);
	}

	/** 퇴근 처리와 함께 WORKED 로 바뀐다. */
	public ShiftResponse clockOut(long id, LocalDateTime at) {
		StaffShift s = find(id);
		s.clockOut(at == null ? now() : at);
		return toResponse(s);
	}

	public ShiftResponse markAbsent(long id) {
		StaffShift s = find(id);
		s.markAbsent();
		return toResponse(s);
	}

	public ShiftResponse cancel(long id) {
		StaffShift s = find(id);
		s.cancel();
		return toResponse(s);
	}

	private static void validateTimes(LocalTime start, LocalTime end) {
		if (start.equals(end))
			throw new IllegalArgumentException("근무 시작 시각과 종료 시각이 같을 수 없습니다.");
	}

	/**
	 * 같은 직원의 예정·완료 근무와 시간 구간이 겹치는지 검사한다. 야간 근무가 날짜를 넘을 수 있으므로 전날~다음 날 일정을 함께 본다.
	 * 호출 전에 직원 행을 잠가 동시에 등록되는 일정끼리도 겹치지 않게 한다.
	 */
	private void requireNoOverlap(StaffShift target) {
		LocalDateTime from = target.scheduledFrom();
		LocalDateTime to = target.scheduledTo();
		LocalDate day = target.getWorkDate();
		for (StaffShift other : repo.findByStaffIdAndWorkDateBetweenAndStatusIn(target.getStaffId(), day.minusDays(1),
				day.plusDays(1), OCCUPYING)) {
			if (other.getId() != null && other.getId().equals(target.getId()))
				continue;
			if (other.scheduledFrom().isBefore(to) && from.isBefore(other.scheduledTo()))
				throw new ConflictException("같은 직원의 다른 근무(" + other.getWorkDate() + " " + other.getScheduledStart()
						+ "~" + other.getScheduledEnd() + ")와 시간이 겹칩니다.");
		}
	}

	private ShiftResponse toResponse(StaffShift s) {
		return ShiftResponse.of(s, staffRepo.findById(s.getStaffId()).orElse(null));
	}

	private StaffShift find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("근무 일정", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
