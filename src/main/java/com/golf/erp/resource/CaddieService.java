package com.golf.erp.resource;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.resource.ResourceDtos.CaddieCreateRequest;
import com.golf.erp.resource.ResourceDtos.CaddieResponse;
import com.golf.erp.resource.ResourceDtos.CaddieUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional
public class CaddieService {
	private final CaddieRepository repo;
	private final AssignmentLookup assignments;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public CaddieService(CaddieRepository repo, AssignmentLookup assignments, RefChecker refs, AuditLogger audit,
			Clock clock) {
		this.repo = repo;
		this.assignments = assignments;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * @param available true 면 근무 중(ACTIVE)이면서 지금 배정되지 않은 캐디만, false 면 배정 중인 캐디만
	 */
	@Transactional(readOnly = true)
	public PageResult<CaddieResponse> list(Long golfCourseId, CaddieStatus status, String keyword, Boolean available,
			int page, int size) {
		Map<Long, Long> busy = assignments.activeCaddies();
		Specification<Caddie> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("status", status), Specs.like(keyword, "name", "caddieNo"));
		if (Boolean.TRUE.equals(available)) {
			spec = spec.and(Specs.eq("status", CaddieStatus.ACTIVE));
			if (!busy.isEmpty())
				spec = spec.and(Specification.not(Specs.in("id", busy.keySet())));
		} else if (Boolean.FALSE.equals(available)) {
			spec = spec.and(busy.isEmpty() ? (r, q, cb) -> cb.disjunction() : Specs.in("id", busy.keySet()));
		}
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by("caddieNo"))),
				c -> CaddieResponse.of(c, busy.get(c.getId())));
	}

	@Transactional(readOnly = true)
	public CaddieResponse get(long id) {
		return toResponse(find(id));
	}

	public CaddieResponse create(CaddieCreateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		String no = r.caddieNo().trim();
		if (repo.existsByGolfCourseIdAndCaddieNo(r.golfCourseId(), no))
			throw new ConflictException("이미 있는 캐디 번호입니다: " + no);
		LocalDateTime now = now();
		Caddie c = new Caddie(r.golfCourseId(), no, now);
		c.update(r.name().trim(), blankToNull(r.phone()), blankToNull(r.grade()), r.hiredOn(), blankToNull(r.memo()),
				now);
		return CaddieResponse.of(repo.save(c), null);
	}

	public CaddieResponse update(long id, CaddieUpdateRequest r) {
		Caddie c = find(id);
		c.update(r.name().trim(), blankToNull(r.phone()), blankToNull(r.grade()), r.hiredOn(), blankToNull(r.memo()),
				now());
		return toResponse(c);
	}

	/** 라운드에 배정 중인 캐디는 휴직·퇴직으로 바꿀 수 없다(먼저 배정 해제). */
	public CaddieResponse changeStatus(long id, CaddieStatus status) {
		Caddie c = find(id);
		Long roundId = assignments.activeCaddies().get(id);
		if (status != CaddieStatus.ACTIVE && roundId != null)
			throw new ConflictException("라운드(" + roundId + ")에 배정 중인 캐디입니다. 배정을 먼저 해제하세요.");
		CaddieStatus before = c.getStatus();
		c.changeStatus(status, now());
		audit.log("STATUS_CHANGE", "caddie", id, Map.of("before", before, "after", status));
		return CaddieResponse.of(c, roundId);
	}

	private CaddieResponse toResponse(Caddie c) {
		return CaddieResponse.of(c, assignments.activeCaddies().get(c.getId()));
	}

	private Caddie find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("캐디", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
