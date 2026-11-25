package com.golf.erp.teetime;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.course.GolfCourse;
import com.golf.erp.course.GolfCourseClosure;
import com.golf.erp.course.GolfCourseService;
import com.golf.erp.fee.GreenFeePolicy;
import com.golf.erp.fee.GreenFeePolicyService;
import com.golf.erp.redis.TeeTimeChangeNotifier;
import com.golf.erp.reservation.ReservationRepository;
import com.golf.erp.teetime.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional
public class TeeTimeService {
	private static final int MAX_GENERATE_DAYS = 62;
	private static final int INSERT_CHUNK = 1000;
	private static final String DEFAULT_HOLE_TYPE = "18H";
	private static final short DEFAULT_CAPACITY = 4;

	private final TeeTimeRepository repo;
	private final TeeTimeMapper mapper;
	private final GolfCourseService courseService;
	private final GreenFeePolicyService feeService;
	private final ReservationRepository reservationRepo;
	private final TeeTimeChangeNotifier notifier;

	public TeeTimeService(TeeTimeRepository repo, TeeTimeMapper mapper, GolfCourseService courseService,
			GreenFeePolicyService feeService, ReservationRepository reservationRepo, TeeTimeChangeNotifier notifier) {
		this.repo = repo;
		this.mapper = mapper;
		this.courseService = courseService;
		this.feeService = feeService;
		this.reservationRepo = reservationRepo;
		this.notifier = notifier;
	}

	@Transactional(readOnly = true)
	public TeeTimeBoard board(Long courseId, LocalDate from, LocalDate to, TeeTimeStatus status) {
		if (to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		if (ChronoUnit.DAYS.between(from, to) > 31)
			throw new IllegalArgumentException("조회 기간은 최대 31일입니다.");
		List<TeeTimeBoardRow> rows = mapper.selectBoard(courseId, from, to, status == null ? null : status.name());
		return new TeeTimeBoard(courseId, from, to, rows.size(), count(rows, TeeTimeStatus.OPEN),
				count(rows, TeeTimeStatus.BOOKED), count(rows, TeeTimeStatus.BLOCKED),
				count(rows, TeeTimeStatus.CLOSED), rows);
	}

	@Transactional(readOnly = true)
	public TeeTime get(long id) {
		return repo.findById(id).orElseThrow(() -> new NotFoundException("티타임을 찾을 수 없습니다: " + id));
	}

	public TeeTime create(TeeTimeRequest r) {
		GolfCourse course = courseService.get(r.golfCourseId());
		LocalDateTime at = r.playDate().atTime(r.startTime());
		if (!courseService.activeClosures(course.getGolfCourseId(), at, at.plusMinutes(1)).isEmpty())
			throw new ConflictException("휴장 기간에는 티타임을 만들 수 없습니다.");
		BigDecimal fee = r.greenFee() != null ? r.greenFee() : policyFee(course.getGolfCourseId(), at);
		TeeTime t = repo.saveAndFlush(new TeeTime(course.getGolfCourseId(), blankToNull(r.course()), r.playDate(),
				r.startTime(), r.holeType() == null ? DEFAULT_HOLE_TYPE : r.holeType(),
				r.capacity() == null ? DEFAULT_CAPACITY : r.capacity(), fee));
		notifyChange("TEE_TIME_CREATED", t);
		return t;
	}

	public TeeTimeGenerateResult generate(TeeTimeGenerateRequest r) {
		GolfCourse course = courseService.get(r.golfCourseId());
		if (r.toDate().isBefore(r.fromDate()))
			throw new IllegalArgumentException("종료일은 시작일 이후여야 합니다.");
		if (ChronoUnit.DAYS.between(r.fromDate(), r.toDate()) >= MAX_GENERATE_DAYS)
			throw new IllegalArgumentException("한 번에 최대 " + MAX_GENERATE_DAYS + "일까지 생성할 수 있습니다.");
		if (r.lastTime().isBefore(r.firstTime()))
			throw new IllegalArgumentException("마지막 티타임은 첫 티타임 이후여야 합니다.");

		long courseId = course.getGolfCourseId();
		List<GolfCourseClosure> closures = courseService.activeClosures(courseId, r.fromDate().atStartOfDay(),
				r.toDate().plusDays(1).atStartOfDay());
		String holeType = r.holeType() == null ? DEFAULT_HOLE_TYPE : r.holeType();
		short capacity = r.capacity() == null ? DEFAULT_CAPACITY : r.capacity();

		List<TeeTime> rows = new ArrayList<>();
		int skippedClosure = 0;
		int withoutFee = 0;
		for (LocalDate d = r.fromDate(); !d.isAfter(r.toDate()); d = d.plusDays(1)) {
			if (r.daysOfWeek() != null && !r.daysOfWeek().isEmpty()
					&& !r.daysOfWeek().contains(d.getDayOfWeek().getValue()))
				continue;
			for (LocalTime t : slots(r.firstTime(), r.lastTime(), r.intervalMinutes())) {
				LocalDateTime at = d.atTime(t);
				if (closures.stream().anyMatch(c -> !at.isBefore(c.getClosureStart()) && at.isBefore(c.getClosureEnd()))) {
					skippedClosure++;
					continue;
				}
				BigDecimal fee = r.greenFee() != null ? r.greenFee() : policyFee(courseId, at);
				if (fee == null)
					withoutFee++;
				rows.add(new TeeTime(courseId, blankToNull(r.course()), d, t, holeType, capacity, fee));
			}
		}
		int created = 0;
		for (int i = 0; i < rows.size(); i += INSERT_CHUNK)
			created += mapper.insertIgnoreDuplicates(rows.subList(i, Math.min(i + INSERT_CHUNK, rows.size())));
		if (created > 0)
			notifier.teeTimesGenerated(courseId, rows.stream().map(TeeTime::getPlayDate).collect(Collectors.toCollection(TreeSet::new)));
		int requested = rows.size() + skippedClosure;
		return new TeeTimeGenerateResult(requested, created, rows.size() - created, skippedClosure, withoutFee);
	}

	public TeeTime update(long id, TeeTimeUpdateRequest r) {
		TeeTime t = get(id);
		boolean booked = t.getTeeTimeStatus() == TeeTimeStatus.BOOKED;
		if (r.status() != null && r.status() != t.getTeeTimeStatus()) {
			if (!r.status().isAdminSettable())
				throw new IllegalArgumentException("BOOKED 상태는 예약 생성으로만 바뀝니다.");
			if (booked)
				throw new ConflictException("예약된 티타임은 상태를 바꿀 수 없습니다. 예약을 먼저 취소하세요.");
			t.changeStatus(r.status());
		}
		if (r.capacity() != null && !r.capacity().equals(t.getCapacity())) {
			if (booked)
				throw new ConflictException("예약된 티타임은 정원을 바꿀 수 없습니다.");
			t.changeCapacity(r.capacity());
		}
		if (r.greenFee() != null)
			t.changeGreenFee(r.greenFee());
		if (r.holeType() != null)
			t.changeHoleType(r.holeType());
		notifyChange("TEE_TIME_UPDATED", t);
		return t;
	}

	/** 예약 이력이 한 건이라도 있으면 삭제하지 않는다(대신 CLOSED). */
	public void delete(long id) {
		TeeTime t = get(id);
		if (t.getTeeTimeStatus() == TeeTimeStatus.BOOKED || reservationRepo.existsByTeeTimeId(id))
			throw new ConflictException("예약 이력이 있는 티타임은 삭제할 수 없습니다. CLOSED 로 변경하세요.");
		repo.delete(t);
		notifyChange("TEE_TIME_DELETED", t);
	}

	/** 커밋 후 예약 앱 티타임 캐시 삭제 + 예약 현황 실시간 알림 */
	private void notifyChange(String type, TeeTime t) {
		notifier.teeTimeChanged(type, t.getGolfCourseId(), t.getPlayDate(), t.getTeeTimeId(), null,
				t.getTeeTimeStatus().name());
	}

	private BigDecimal policyFee(long courseId, LocalDateTime at) {
		return feeService.resolve(courseId, at.toLocalDate(), at.toLocalTime()).map(GreenFeePolicy::netFee)
				.orElse(null);
	}

	static List<LocalTime> slots(LocalTime first, LocalTime last, int intervalMinutes) {
		List<LocalTime> slots = new ArrayList<>();
		for (LocalTime t = first; !t.isAfter(last); t = t.plusMinutes(intervalMinutes)) {
			slots.add(t);
			if (t.plusMinutes(intervalMinutes).isBefore(t))
				break; // 자정을 넘기면 중단
		}
		return slots;
	}

	private static int count(List<TeeTimeBoardRow> rows, TeeTimeStatus status) {
		return (int) rows.stream().filter(r -> status.name().equals(r.teeTimeStatus())).count();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
