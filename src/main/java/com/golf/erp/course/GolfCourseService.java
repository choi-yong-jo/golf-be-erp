package com.golf.erp.course;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.course.dto.ClosureRequest;
import com.golf.erp.course.dto.CourseRequest;
import com.golf.erp.course.dto.CourseStatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class GolfCourseService {
	private final GolfCourseRepository repo;
	private final GolfCourseClosureRepository closureRepo;
	private final Clock clock;

	public GolfCourseService(GolfCourseRepository repo, GolfCourseClosureRepository closureRepo, Clock clock) {
		this.repo = repo;
		this.closureRepo = closureRepo;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<GolfCourse> findAll() {
		return repo.findAllByOrderByGolfCourseIdAsc();
	}

	@Transactional(readOnly = true)
	public GolfCourse get(long id) {
		return repo.findById(id).orElseThrow(() -> new NotFoundException("코스를 찾을 수 없습니다: " + id));
	}

	public GolfCourse create(CourseRequest r) {
		if (repo.existsByCourseCode(r.courseCode().trim()))
			throw new ConflictException("이미 사용 중인 코스 코드입니다.");
		return repo.save(new GolfCourse(r.courseCode().trim(), r.courseName().trim(), r.address(), r.phone(),
				r.holeCount() == null ? 18 : r.holeCount(), now()));
	}

	public GolfCourse update(long id, CourseRequest r) {
		GolfCourse c = get(id);
		if (repo.existsByCourseCodeAndGolfCourseIdNot(r.courseCode().trim(), id))
			throw new ConflictException("이미 사용 중인 코스 코드입니다.");
		c.update(r.courseCode().trim(), r.courseName().trim(), r.address(), r.phone(),
				r.holeCount() == null ? c.getHoleCount() : r.holeCount(), now());
		return c;
	}

	public GolfCourse changeStatus(long id, CourseStatusRequest r) {
		GolfCourse c = get(id);
		c.changeStatus(r.courseStatus(), r.active(), now());
		return c;
	}

	// ---------- 휴장 ----------

	@Transactional(readOnly = true)
	public List<GolfCourseClosure> closures(long courseId) {
		get(courseId);
		return closureRepo.findByGolfCourseIdOrderByClosureStartDesc(courseId);
	}

	public GolfCourseClosure addClosure(long courseId, ClosureRequest r) {
		get(courseId);
		if (!r.closureEnd().isAfter(r.closureStart()))
			throw new IllegalArgumentException("휴장 종료 시각은 시작 시각 이후여야 합니다.");
		String type = r.closureType() == null || r.closureType().isBlank() ? "CLOSED" : r.closureType().trim();
		return closureRepo.save(new GolfCourseClosure(courseId, r.closureStart(), r.closureEnd(), type, r.reason()));
	}

	/** 휴장 해제(비활성). 이력 보존을 위해 행은 남긴다. */
	public GolfCourseClosure deactivateClosure(long courseId, long closureId) {
		GolfCourseClosure c = closureRepo.findById(closureId)
				.filter(x -> x.getGolfCourseId().equals(courseId))
				.orElseThrow(() -> new NotFoundException("휴장 정보를 찾을 수 없습니다: " + closureId));
		c.deactivate();
		return c;
	}

	/** at 시각이 활성 휴장 구간에 포함되면 해당 휴장을 반환 */
	@Transactional(readOnly = true)
	public List<GolfCourseClosure> activeClosures(long courseId, LocalDateTime from, LocalDateTime to) {
		return closureRepo.findActiveOverlapping(courseId, from, to);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
