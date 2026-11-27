package com.golf.erp.teetime;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.fee.GreenFeePolicyService;
import com.golf.erp.fee.GreenFeeTable;
import com.golf.erp.teetime.TeeTimeDtos.ReservationSummary;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeBoard;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeCreateRequest;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeGenerateRequest;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeGenerateResult;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeResponse;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeStatusRequest;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeUpdateRequest;
import com.golf.erp.teetime.TeeTimeSlotQueries.ClosurePeriod;
import com.golf.erp.teetime.TeeTimeSlotQueries.GolfCourseInfo;
import com.golf.erp.teetime.TeeTimeSlotQueries.NewSlot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class TeeTimeService {
	private static final int MAX_BOARD_DAYS = 31;
	private static final int MAX_GENERATE_DAYS = 62;
	private static final int INSERT_CHUNK = 2000;
	private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final TeeTimeRepository repo;
	private final TeeTimeSlotQueries queries;
	private final GreenFeePolicyService fees;
	private final AuditLogger audit;
	private final Clock clock;

	public TeeTimeService(TeeTimeRepository repo, TeeTimeSlotQueries queries, GreenFeePolicyService fees,
			AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.queries = queries;
		this.fees = fees;
		this.audit = audit;
		this.clock = clock;
	}

	/** 예약 현황: 기간(최대 31일)의 티타임과 각 티타임의 활성 예약 */
	@Transactional(readOnly = true)
	public TeeTimeBoard board(Long golfCourseId, String course, LocalDate from, LocalDate to, TeeTimeStatus status) {
		if (to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		if (ChronoUnit.DAYS.between(from, to) >= MAX_BOARD_DAYS)
			throw new IllegalArgumentException("조회 기간은 최대 " + MAX_BOARD_DAYS + "일입니다.");
		List<TeeTimeResponse> rows = queries.board(golfCourseId, normalizeCourse(course), from, to, status);
		return new TeeTimeBoard(golfCourseId, from, to, rows.size(), count(rows, TeeTimeStatus.OPEN),
				count(rows, TeeTimeStatus.BOOKED), count(rows, TeeTimeStatus.BLOCKED),
				count(rows, TeeTimeStatus.CLOSED), rows);
	}

	@Transactional(readOnly = true)
	public TeeTimeResponse get(long id) {
		TeeTime t = repo.findById(id).orElseThrow(() -> NotFoundException.of("티타임", id));
		return toResponse(t, queries.activeReservation(id).orElse(null));
	}

	/**
	 * 티타임 1건 등록. 활성 골프장·등록된 코스 코드·미래 시각·휴장 아님·슬롯 중복 없음을 확인한다.
	 * greenFee 생략 시 요금 정책(기본 요금 - 할인)으로 채운다.
	 */
	public TeeTimeResponse create(TeeTimeCreateRequest r) {
		GolfCourseInfo gc = requireActiveGolfCourse(r.golfCourseId());
		String course = requireCourse(gc.id(), r.course());
		LocalDateTime at = r.playDate().atTime(r.startTime());
		if (at.isBefore(now()))
			throw new IllegalArgumentException("이미 지난 시각에는 티타임을 만들 수 없습니다.");
		List<ClosurePeriod> closures = queries.activeClosures(gc.id(), at, at.plusMinutes(1));
		if (!closures.isEmpty())
			throw new ConflictException("휴장 기간에는 티타임을 만들 수 없습니다: " + describe(closures.get(0)));
		if (repo.existsSlot(gc.id(), r.playDate(), r.startTime(), course == null ? "" : course))
			throw new ConflictException("이미 같은 시각의 티타임이 있습니다.");
		BigDecimal fee = r.greenFee() != null ? money(r.greenFee()) : fees.perPlayerFee(gc.id(), at).orElse(null);
		TeeTime t = new TeeTime(gc.id(), course, r.playDate(), r.startTime());
		t.update(r.holeType() == null ? TeeTime.DEFAULT_HOLE_TYPE : r.holeType(),
				r.capacity() == null ? TeeTime.DEFAULT_CAPACITY : r.capacity(), fee);
		repo.saveAndFlush(t);
		return toResponse(t, gc.name(), null);
	}

	/**
	 * 기간 일괄 생성. 이미 있는 슬롯(ux_tee_time_slot)·휴장 구간·지난 시각은 건너뛴다.
	 * greenFee 생략 시 슬롯마다 요금 정책으로 채우고, 맞는 정책이 없으면 비워 둔다.
	 */
	public TeeTimeGenerateResult generate(TeeTimeGenerateRequest r) {
		if (r.toDate().isBefore(r.fromDate()))
			throw new IllegalArgumentException("종료일은 시작일 이후여야 합니다.");
		if (ChronoUnit.DAYS.between(r.fromDate(), r.toDate()) >= MAX_GENERATE_DAYS)
			throw new IllegalArgumentException("한 번에 최대 " + MAX_GENERATE_DAYS + "일까지 생성할 수 있습니다.");
		if (r.lastTime().isBefore(r.firstTime()))
			throw new IllegalArgumentException("마지막 티타임은 첫 티타임 이후여야 합니다.");
		GolfCourseInfo gc = requireActiveGolfCourse(r.golfCourseId());
		String course = requireCourse(gc.id(), r.course());
		long golfCourseId = gc.id();

		List<ClosurePeriod> closures = queries.activeClosures(golfCourseId, r.fromDate().atStartOfDay(),
				r.toDate().plusDays(1).atStartOfDay());
		BigDecimal fixedFee = r.greenFee() == null ? null : money(r.greenFee());
		GreenFeeTable feeTable = fixedFee != null ? null : fees.table(golfCourseId, r.fromDate(), r.toDate());
		Set<Integer> days = r.daysOfWeek();
		List<LocalTime> times = slots(r.firstTime(), r.lastTime(), r.intervalMinutes());
		LocalDateTime now = now();

		List<NewSlot> rows = new ArrayList<>();
		int requested = 0;
		int skippedClosure = 0;
		int skippedPast = 0;
		int withoutFee = 0;
		for (LocalDate d = r.fromDate(); !d.isAfter(r.toDate()); d = d.plusDays(1)) {
			if (days != null && !days.isEmpty() && !days.contains(d.getDayOfWeek().getValue()))
				continue;
			for (LocalTime t : times) {
				requested++;
				LocalDateTime at = d.atTime(t);
				if (at.isBefore(now)) {
					skippedPast++;
					continue;
				}
				if (closures.stream().anyMatch(c -> c.contains(at))) {
					skippedClosure++;
					continue;
				}
				BigDecimal fee = fixedFee != null ? fixedFee : feeTable.netFeeAt(d, t).orElse(null);
				if (fee == null)
					withoutFee++;
				rows.add(new NewSlot(d, t, fee));
			}
		}
		String holeType = r.holeType() == null ? TeeTime.DEFAULT_HOLE_TYPE : r.holeType();
		short capacity = r.capacity() == null ? TeeTime.DEFAULT_CAPACITY : r.capacity();
		int created = 0;
		for (int i = 0; i < rows.size(); i += INSERT_CHUNK)
			created += queries.insertIgnoringDuplicates(golfCourseId, course, holeType, capacity,
					rows.subList(i, Math.min(i + INSERT_CHUNK, rows.size())));
		TeeTimeGenerateResult result = new TeeTimeGenerateResult(requested, created, rows.size() - created,
				skippedClosure, skippedPast, withoutFee);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("course", course == null ? "" : course);
		details.put("fromDate", r.fromDate().toString());
		details.put("toDate", r.toDate().toString());
		details.put("created", created);
		audit.log("GENERATE_TEE_TIMES", "golf_course", golfCourseId, details);
		return result;
	}

	/**
	 * 홀 유형·정원·그린피 수정(슬롯 자체는 바꾸지 않는다). 활성 예약이 있으면 정원을 예약 인원보다 줄일 수 없다.
	 * 그린피를 바꿔도 이미 받은 예약의 금액은 바뀌지 않는다(예약에 따로 기록됨).
	 */
	public TeeTimeResponse update(long id, TeeTimeUpdateRequest r) {
		TeeTime t = lock(id);
		ReservationSummary reservation = queries.activeReservation(id).orElse(null);
		if (reservation != null && reservation.playerCount() != null && r.capacity() < reservation.playerCount())
			throw new ConflictException("예약 인원(" + reservation.playerCount() + "명)보다 정원을 작게 줄일 수 없습니다.");
		t.update(r.holeType(), r.capacity(), r.greenFee() == null ? null : money(r.greenFee()));
		repo.flush(); // 응답에 올라간 version 을 돌려주려고
		return toResponse(t, reservation);
	}

	/**
	 * 상태 변경(OPEN/BLOCKED/CLOSED). 활성 예약이 있는 티타임은 바꿀 수 없다(예약을 먼저 취소).
	 * OPEN 으로 되돌릴 때는 지난 시각·휴장 구간이 아닌지 확인한다.
	 */
	public TeeTimeResponse changeStatus(long id, TeeTimeStatusRequest r) {
		if (!r.status().isAdminSettable())
			throw new IllegalArgumentException("BOOKED 상태는 예약 생성으로만 바뀝니다.");
		TeeTime t = lock(id);
		Optional<ReservationSummary> reservation = queries.activeReservation(id);
		if (reservation.isPresent())
			throw new ConflictException(
					"활성 예약(" + reservationLabel(reservation.get()) + ")이 있는 티타임은 상태를 바꿀 수 없습니다. 예약을 먼저 취소하세요.");
		if (r.status() == TeeTimeStatus.OPEN) {
			if (t.playAt().isBefore(now()))
				throw new ConflictException("이미 지난 티타임은 열 수 없습니다.");
			if (t.getGolfCourseId() != null) {
				List<ClosurePeriod> closures = queries.activeClosures(t.getGolfCourseId(), t.playAt(),
						t.playAt().plusMinutes(1));
				if (!closures.isEmpty())
					throw new ConflictException("휴장 기간의 티타임은 열 수 없습니다: " + describe(closures.get(0)));
			}
		}
		TeeTimeStatus before = t.getStatus();
		t.changeStatus(r.status());
		repo.flush();
		audit.log("STATUS_CHANGE", "tee_time", id, Map.of("before", before, "after", r.status()));
		return toResponse(t, null);
	}

	/** 예약(취소 포함)·라운드 이력이 한 건이라도 있으면 삭제하지 않는다(대신 CLOSED). */
	public void delete(long id) {
		TeeTime t = lock(id);
		if (t.getStatus() == TeeTimeStatus.BOOKED || queries.isReferenced(id))
			throw new ConflictException("예약·라운드 이력이 있는 티타임은 삭제할 수 없습니다. CLOSED 로 변경하세요.");
		repo.delete(t);
		audit.log("DELETE", "tee_time", id, Map.of("golfCourseId", t.getGolfCourseId() == null ? 0L
				: t.getGolfCourseId(), "playAt", t.playAt().toString(), "course",
				t.getCourse() == null ? "" : t.getCourse()));
	}

	/** first~last 를 interval 분 간격으로. 자정을 넘기면 멈춘다. */
	static List<LocalTime> slots(LocalTime first, LocalTime last, int intervalMinutes) {
		List<LocalTime> slots = new ArrayList<>();
		for (LocalTime t = first; !t.isAfter(last); t = t.plusMinutes(intervalMinutes)) {
			slots.add(t);
			if (t.plusMinutes(intervalMinutes).isBefore(t))
				break;
		}
		return slots;
	}

	private GolfCourseInfo requireActiveGolfCourse(long golfCourseId) {
		GolfCourseInfo gc = queries.golfCourse(golfCourseId);
		if (!gc.active())
			throw new ConflictException("비활성 골프장에는 티타임을 만들 수 없습니다.");
		return gc;
	}

	/** course 가 있으면 골프장의 활성 코스 코드여야 한다(대문자로 정규화). 없으면 null */
	private String requireCourse(long golfCourseId, String course) {
		String code = normalizeCourse(course);
		if (code != null && !queries.activeCourseCodes(golfCourseId).contains(code))
			throw new IllegalArgumentException("골프장에 등록된 활성 코스가 아닙니다: " + code);
		return code;
	}

	private TeeTime lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("티타임", id));
	}

	private TeeTimeResponse toResponse(TeeTime t, ReservationSummary reservation) {
		return toResponse(t, queries.golfCourseName(t.getGolfCourseId()), reservation);
	}

	private static TeeTimeResponse toResponse(TeeTime t, String golfCourseName, ReservationSummary reservation) {
		return new TeeTimeResponse(t.getId(), t.getGolfCourseId(), golfCourseName, t.getCourse(), t.getPlayDate(),
				t.getStartTime(), t.getHoleType(), t.getCapacity(), t.getGreenFee(), t.getStatus(), t.getVersion(),
				reservation);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String describe(ClosurePeriod c) {
		return c.start().format(PERIOD) + " ~ " + c.end().format(PERIOD) + " (" + c.reason() + ")";
	}

	private static String reservationLabel(ReservationSummary r) {
		return r.reservationNo() != null ? r.reservationNo() : String.valueOf(r.reservationId());
	}

	private static BigDecimal money(BigDecimal v) {
		return v.setScale(2, RoundingMode.HALF_UP);
	}

	private static String normalizeCourse(String course) {
		return course == null || course.isBlank() ? null : course.trim().toUpperCase(Locale.ROOT);
	}

	private static int count(List<TeeTimeResponse> rows, TeeTimeStatus status) {
		return (int) rows.stream().filter(r -> r.status() == status).count();
	}
}
