package com.golf.erp.course;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Specs;
import com.golf.erp.course.CourseDtos.CourseCreateRequest;
import com.golf.erp.course.CourseDtos.CourseResponse;
import com.golf.erp.course.CourseDtos.CourseStatusRequest;
import com.golf.erp.course.CourseDtos.CourseUpdateRequest;
import com.golf.erp.course.CourseDtos.HoleBulkRequest;
import com.golf.erp.course.CourseDtos.HoleRequest;
import com.golf.erp.course.CourseDtos.HoleResponse;
import com.golf.erp.course.CourseDtos.HoleSummary;
import com.golf.erp.course.CourseDtos.HoleUpdateRequest;
import com.golf.erp.course.CourseDtos.ScorecardResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 코스(9홀 단위)와 홀(스코어카드) */
@Service
@Transactional
public class CourseService {
	private final CourseRepository repo;
	private final HoleRepository holeRepo;
	private final GolfCourseRepository golfCourseRepo;
	private final CourseUsageLookup usage;
	private final AuditLogger audit;
	private final Clock clock;

	public CourseService(CourseRepository repo, HoleRepository holeRepo, GolfCourseRepository golfCourseRepo,
			CourseUsageLookup usage, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.holeRepo = holeRepo;
		this.golfCourseRepo = golfCourseRepo;
		this.usage = usage;
		this.audit = audit;
		this.clock = clock;
	}

	/** 코스는 골프장당 몇 개뿐이라 목록 전체를 돌려준다(골프장·표시 순서·코드 순). */
	@Transactional(readOnly = true)
	public List<CourseResponse> list(Long golfCourseId, Boolean active, CourseStatus status) {
		Specification<Course> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("active", active), Specs.eq("status", status));
		List<Course> courses = repo.findAll(spec, Sort.by("golfCourseId", "sortOrder", "courseCode"));
		Map<Long, HoleSummary> summaries = summarize(courses.stream().map(Course::getId).toList());
		return courses.stream()
				.map(c -> CourseResponse.of(c, summaries.getOrDefault(c.getId(), HoleSummary.EMPTY)))
				.toList();
	}

	@Transactional(readOnly = true)
	public CourseResponse get(long id) {
		return toResponse(find(id));
	}

	public CourseResponse create(CourseCreateRequest r) {
		if (!golfCourseRepo.existsById(r.golfCourseId()))
			throw NotFoundException.of("골프장", r.golfCourseId());
		String code = normalizeCode(r.courseCode());
		if (repo.existsByGolfCourseIdAndCourseCode(r.golfCourseId(), code))
			throw new ConflictException("이 골프장에 이미 있는 코스 코드입니다: " + code);
		LocalDateTime now = now();
		Course c = new Course(r.golfCourseId(), now);
		c.update(code, r.courseName().trim(), r.holeCount() == null ? Course.DEFAULT_HOLE_COUNT : r.holeCount(),
				r.sortOrder() == null ? 0 : r.sortOrder(), now);
		return CourseResponse.of(repo.save(c), HoleSummary.EMPTY);
	}

	/**
	 * 코스 코드는 티타임(tee_time.course)과 값으로 연결되므로, 이 코드를 쓰는 티타임이 있으면 바꿀 수 없다.
	 * 홀 수는 이미 등록된 가장 큰 홀 번호보다 작게 줄일 수 없다.
	 */
	public CourseResponse update(long id, CourseUpdateRequest r) {
		Course c = find(id);
		String code = normalizeCode(r.courseCode());
		if (!code.equals(c.getCourseCode())) {
			if (repo.existsByGolfCourseIdAndCourseCodeAndIdNot(c.getGolfCourseId(), code, id))
				throw new ConflictException("이 골프장에 이미 있는 코스 코드입니다: " + code);
			if (usage.teeTimeUsesCourseCode(c.getGolfCourseId(), c.getCourseCode()))
				throw new ConflictException("티타임에 쓰이고 있는 코스 코드는 바꿀 수 없습니다: " + c.getCourseCode());
		}
		HoleSummary s = summary(id);
		if (r.holeCount() < s.maxHoleNo())
			throw new ConflictException("등록된 홀 번호(" + s.maxHoleNo() + "번)보다 홀 수를 작게 줄일 수 없습니다.");
		c.update(code, r.courseName().trim(), r.holeCount(),
				r.sortOrder() == null ? c.getSortOrder() : r.sortOrder(), now());
		return CourseResponse.of(c, s);
	}

	public CourseResponse changeStatus(long id, CourseStatusRequest r) {
		Course c = find(id);
		CourseStatus before = c.getStatus();
		boolean beforeActive = c.isActive();
		LocalDateTime now = now();
		c.changeStatus(r.status(), now);
		if (r.active() != null)
			c.changeActive(r.active(), now);
		audit.log("STATUS_CHANGE", "course", id, Map.of("before", before, "after", r.status(), "activeBefore",
				beforeActive, "activeAfter", c.isActive()));
		return toResponse(c);
	}

	/** 삭제 대신 비활성(is_active=false). 라운드·스코어 이력이 코스를 참조하므로 행은 남긴다. */
	public void deactivate(long id) {
		Course c = find(id);
		if (!c.isActive())
			return;
		c.changeActive(false, now());
		audit.log("DEACTIVATE", "course", id, Map.of("courseCode", c.getCourseCode()));
	}

	// ---------- 홀 ----------

	@Transactional(readOnly = true)
	public ScorecardResponse holes(long courseId) {
		return scorecard(find(courseId));
	}

	/**
	 * 스코어카드 일괄 저장(홀 번호 기준 upsert). 홀 번호는 1~코스 홀 수, 요청 안에서 중복될 수 없다.
	 * 저장 후 코스 안에서 핸디캡(난이도 순위)이 겹치면 400.
	 */
	public ScorecardResponse saveHoles(long courseId, HoleBulkRequest r) {
		Course course = find(courseId);
		Set<Short> seen = new HashSet<>();
		for (HoleRequest h : r.holes()) {
			if (h.holeNo() > course.getHoleCount())
				throw new IllegalArgumentException(
						"홀 번호는 1~" + course.getHoleCount() + " 사이여야 합니다: " + h.holeNo());
			if (!seen.add(h.holeNo()))
				throw new IllegalArgumentException("홀 번호가 중복되었습니다: " + h.holeNo());
		}
		Map<Short, Hole> existing = holeRepo.findByCourseIdOrderByHoleNo(courseId).stream()
				.collect(Collectors.toMap(Hole::getHoleNo, Function.identity()));
		LocalDateTime now = now();
		for (HoleRequest h : r.holes()) {
			Hole hole = existing.get(h.holeNo());
			if (hole == null) {
				hole = new Hole(courseId, h.holeNo(), now);
				existing.put(h.holeNo(), hole);
			}
			hole.update(h.par(), h.handicap(), h.distanceBack(), h.distanceRegular(), h.distanceFront(),
					blankToNull(h.memo()), now);
		}
		checkHandicaps(existing.values());
		holeRepo.saveAll(existing.values());
		audit.log("UPDATE", "course", courseId, Map.of("scorecardHoles", r.holes().size()));
		return scorecard(course);
	}

	public HoleResponse updateHole(long holeId, HoleUpdateRequest r) {
		Hole hole = holeRepo.findById(holeId).orElseThrow(() -> NotFoundException.of("홀", holeId));
		hole.update(r.par(), r.handicap(), r.distanceBack(), r.distanceRegular(), r.distanceFront(),
				blankToNull(r.memo()), now());
		checkHandicaps(holeRepo.findByCourseIdOrderByHoleNo(hole.getCourseId()));
		return HoleResponse.of(hole);
	}

	private static void checkHandicaps(Iterable<Hole> holes) {
		Map<Short, Short> byHandicap = new HashMap<>();
		for (Hole h : holes) {
			if (h.getHandicap() == null)
				continue;
			Short other = byHandicap.putIfAbsent(h.getHandicap(), h.getHoleNo());
			if (other != null)
				throw new IllegalArgumentException("핸디캡 " + h.getHandicap() + "이(가) " + other + "번 홀과 "
						+ h.getHoleNo() + "번 홀에 중복되었습니다.");
		}
	}

	private ScorecardResponse scorecard(Course c) {
		List<HoleResponse> holes = holeRepo.findByCourseIdOrderByHoleNo(c.getId()).stream().map(HoleResponse::of)
				.toList();
		int totalPar = holes.stream().mapToInt(HoleResponse::par).sum();
		return new ScorecardResponse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getHoleCount(), totalPar,
				holes);
	}

	private CourseResponse toResponse(Course c) {
		return CourseResponse.of(c, summary(c.getId()));
	}

	private HoleSummary summary(long courseId) {
		return summarize(List.of(courseId)).getOrDefault(courseId, HoleSummary.EMPTY);
	}

	private Map<Long, HoleSummary> summarize(List<Long> courseIds) {
		Map<Long, HoleSummary> m = new HashMap<>();
		if (courseIds.isEmpty())
			return m;
		for (Object[] row : holeRepo.summarize(courseIds))
			m.put((Long) row[0], new HoleSummary(((Number) row[1]).intValue(), ((Number) row[2]).intValue(),
					((Number) row[3]).intValue()));
		return m;
	}

	private Course find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("코스", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
