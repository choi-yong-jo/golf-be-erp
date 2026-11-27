package com.golf.erp.course;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Specs;
import com.golf.erp.course.CourseDtos.ClosureRequest;
import com.golf.erp.course.CourseDtos.ClosureResponse;
import com.golf.erp.course.CourseDtos.GolfCourseRequest;
import com.golf.erp.course.CourseDtos.GolfCourseResponse;
import com.golf.erp.course.CourseDtos.GolfCourseStatusRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class GolfCourseService {
	private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final GolfCourseRepository repo;
	private final GolfCourseClosureRepository closureRepo;
	private final CourseUsageLookup usage;
	private final AuditLogger audit;
	private final Clock clock;

	public GolfCourseService(GolfCourseRepository repo, GolfCourseClosureRepository closureRepo,
			CourseUsageLookup usage, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.closureRepo = closureRepo;
		this.usage = usage;
		this.audit = audit;
		this.clock = clock;
	}

	/** 골프장은 건수가 적은 마스터라 목록 전체를 돌려준다. keyword: 코드·이름 */
	@Transactional(readOnly = true)
	public List<GolfCourseResponse> list(Boolean active, GolfCourseStatus status, String keyword) {
		Specification<GolfCourse> spec = Specification.allOf(Specs.eq("active", active), Specs.eq("status", status),
				Specs.like(keyword, "courseCode", "courseName"));
		return repo.findAll(spec, Sort.by("id")).stream().map(GolfCourseResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public GolfCourseResponse get(long id) {
		return GolfCourseResponse.of(find(id));
	}

	public GolfCourseResponse create(GolfCourseRequest r) {
		String code = r.courseCode().trim();
		if (repo.existsByCourseCode(code))
			throw new ConflictException("이미 사용 중인 골프장 코드입니다: " + code);
		LocalDateTime now = now();
		GolfCourse c = new GolfCourse(code, now);
		c.update(code, r.courseName().trim(), blankToNull(r.address()), blankToNull(r.phone()),
				r.holeCount() == null ? GolfCourse.DEFAULT_HOLE_COUNT : r.holeCount(), now);
		return GolfCourseResponse.of(repo.save(c));
	}

	public GolfCourseResponse update(long id, GolfCourseRequest r) {
		GolfCourse c = find(id);
		String code = r.courseCode().trim();
		if (repo.existsByCourseCodeAndIdNot(code, id))
			throw new ConflictException("이미 사용 중인 골프장 코드입니다: " + code);
		int holeCount = r.holeCount() != null ? r.holeCount()
				: c.getHoleCount() != null ? c.getHoleCount() : GolfCourse.DEFAULT_HOLE_COUNT;
		c.update(code, r.courseName().trim(), blankToNull(r.address()), blankToNull(r.phone()), holeCount, now());
		return GolfCourseResponse.of(c);
	}

	/** 운영 상태·활성 여부 변경. 기존 예약·티타임은 그대로 두며, 새 예약·티타임 생성만 막힌다. */
	public GolfCourseResponse changeStatus(long id, GolfCourseStatusRequest r) {
		GolfCourse c = find(id);
		GolfCourseStatus before = c.getStatus();
		boolean beforeActive = c.isActive();
		boolean active = r.active() == null ? beforeActive : r.active();
		c.changeStatus(r.status(), active, now());
		audit.log("STATUS_CHANGE", "golf_course", id,
				Map.of("before", before, "after", r.status(), "activeBefore", beforeActive, "activeAfter", active));
		return GolfCourseResponse.of(c);
	}

	// ---------- 휴장 ----------

	/** @param activeOnly true 면 해제되지 않은 휴장만 */
	@Transactional(readOnly = true)
	public List<ClosureResponse> closures(long golfCourseId, boolean activeOnly) {
		find(golfCourseId);
		List<GolfCourseClosure> list = activeOnly
				? closureRepo.findByGolfCourseIdAndActiveTrueOrderByClosureStartDesc(golfCourseId)
				: closureRepo.findByGolfCourseIdOrderByClosureStartDesc(golfCourseId);
		return list.stream().map(c -> ClosureResponse.of(c, null)).toList();
	}

	/**
	 * 휴장 등록. 같은 골프장의 활성 휴장과 기간이 겹치면 409(기존 휴장을 해제하거나 기간을 조정).
	 * 이미 지난 구간은 등록할 수 없다. 걸린 활성 예약은 자동 취소하지 않고 건수만 알려 준다.
	 */
	public ClosureResponse addClosure(long golfCourseId, ClosureRequest r) {
		find(golfCourseId);
		if (!r.closureEnd().isAfter(r.closureStart()))
			throw new IllegalArgumentException("휴장 종료 시각은 시작 시각 이후여야 합니다.");
		if (!r.closureEnd().isAfter(now()))
			throw new IllegalArgumentException("이미 지난 기간은 휴장으로 등록할 수 없습니다.");
		List<GolfCourseClosure> overlaps = closureRepo.findActiveOverlapping(golfCourseId, r.closureStart(),
				r.closureEnd());
		if (!overlaps.isEmpty()) {
			GolfCourseClosure o = overlaps.get(0);
			throw new ConflictException("이미 등록된 휴장 기간과 겹칩니다: " + o.getClosureStart().format(PERIOD) + " ~ "
					+ o.getClosureEnd().format(PERIOD) + " (" + o.getReason() + ")");
		}
		String type = r.closureType() == null || r.closureType().isBlank() ? GolfCourseClosure.DEFAULT_TYPE
				: r.closureType().trim().toUpperCase(Locale.ROOT);
		GolfCourseClosure c = closureRepo
				.save(new GolfCourseClosure(golfCourseId, r.closureStart(), r.closureEnd(), type, r.reason().trim()));
		audit.log("CREATE", "golf_course_closure", c.getId(), Map.of("golfCourseId", golfCourseId, "closureStart",
				r.closureStart().toString(), "closureEnd", r.closureEnd().toString()));
		int reservations = usage.activeReservationsBetween(golfCourseId, r.closureStart(), r.closureEnd());
		return ClosureResponse.of(c, reservations);
	}

	/** 휴장 해제(is_active=false). 이력 보존을 위해 행은 남긴다. */
	public void deactivateClosure(long golfCourseId, long closureId) {
		GolfCourseClosure c = closureRepo.findById(closureId).filter(x -> x.getGolfCourseId().equals(golfCourseId))
				.orElseThrow(() -> NotFoundException.of("휴장 정보", closureId));
		c.deactivate();
		audit.log("DEACTIVATE", "golf_course_closure", closureId, Map.of("golfCourseId", golfCourseId));
	}

	GolfCourse find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("골프장", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
