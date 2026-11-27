package com.golf.erp.facility;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.facility.FacilityDtos.AvailableUnitResponse;
import com.golf.erp.facility.FacilityDtos.BookingCreateRequest;
import com.golf.erp.facility.FacilityDtos.BookingResponse;
import com.golf.erp.facility.FacilityDtos.BookingUpdateRequest;
import com.golf.erp.facility.FacilityDtos.CalendarBooking;
import com.golf.erp.facility.FacilityDtos.CalendarResponse;
import com.golf.erp.facility.FacilityDtos.CalendarUnit;
import com.golf.erp.facility.FacilityDtos.UnitResponse;
import com.golf.erp.security.CurrentUser;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시설 예약(facility_booking): 가용 조회, 등록·수정, 확정·입실·퇴실·취소·노쇼, 수납 상태, 달력.
 * <p>
 * <b>겹침 규칙</b>: 예약 기간은 반열린 구간 [start, end). 같은 공간에서 점유 상태(PENDING·CONFIRMED·CHECKED_IN)인 다른
 * 예약과 겹치면(기존.start &lt; 새.end AND 기존.end &gt; 새.start) 409. DB 배타 제약이 없으므로 검사 전에 공간 행을
 * PESSIMISTIC_WRITE 로 잠가 같은 공간의 등록·변경을 직렬화한다. 잠금 순서는 항상 공간 → 예약.
 * <p>
 * <b>금액 규칙</b>({@link FacilityPricing}): 시각은 15분 단위, 이용 시간 = 분/60(소수 둘째 자리 정확).
 * 시간당 요금 = 등록 시점 공간 종류의 시간당 기본요금 스냅샷(원 단위), 기본 금액 = 요금 × 이용 시간(정확),
 * 할인 ≤ 기본 금액, 합계 = 기본 금액 − 할인. 수정 시 스냅샷 요금을 유지하고 시간·금액만 다시 계산한다.
 */
@Service
@Transactional
public class FacilityBookingService {
	/** 달력 조회 최대 기간(일, from·to 포함) */
	static final int MAX_CALENDAR_DAYS = 62;

	private final FacilityBookingRepository repo;
	private final FacilityUnitRepository units;
	private final FacilityRepository facilities;
	private final FacilityLookup lookup;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public FacilityBookingService(FacilityBookingRepository repo, FacilityUnitRepository units,
			FacilityRepository facilities, FacilityLookup lookup, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.units = units;
		this.facilities = facilities;
		this.lookup = lookup;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	// ---------------------------------------------------------------- 조회

	/**
	 * from/to: 예약 기간이 [from 0시, to 다음 날 0시) 와 겹치는 것. keyword: 손님 이름·전화번호·예약 번호.
	 * facilityRoomTypeId: 공간의 현재 종류. 최근 시작 순.
	 */
	@Transactional(readOnly = true)
	public PageResult<BookingResponse> list(Long golfCourseId, Long facilityId, Long facilityRoomTypeId, Long unitId,
			FacilityBookingStatus status, FacilityPaymentStatus paymentStatus, Long memberId, LocalDate from,
			LocalDate to, String keyword, int page, int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일이 시작일보다 앞설 수 없습니다.");
		Specification<FacilityBooking> spec = Specification.allOf(Specs.eq("unit.facility.golfCourseId", golfCourseId),
				Specs.eq("unit.facility.id", facilityId), Specs.eq("unit.roomType.id", facilityRoomTypeId),
				Specs.eq("unit.id", unitId), Specs.eq("status", status), Specs.eq("paymentStatus", paymentStatus),
				Specs.eq("memberId", memberId), Specs.like(keyword, "guestName", "guestPhone", "bookingNo"),
				from == null ? null : endsAfter(from.atStartOfDay()),
				Specs.lt("startAt", to == null ? null : to.plusDays(1).atStartOfDay()));
		Sort sort = Sort.by(Sort.Direction.DESC, "startAt", "id");
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, sort)), BookingResponse::of);
	}

	@Transactional(readOnly = true)
	public BookingResponse get(long id) {
		return BookingResponse.of(repo.findById(id).orElseThrow(() -> NotFoundException.of("시설 예약", id)));
	}

	/**
	 * 예약 가능한 공간: 시설 활성·공간 종류 사용 중·공간 AVAILABLE, 수용 인원 ≥ guestCount, [from, to) 와 겹치는 점유
	 * 예약(PENDING·CONFIRMED·CHECKED_IN)이 없는 공간. from/to 는 15분 단위. 각 공간의 시간당 요금·이용 시간·예상 기본 금액을
	 * 함께 준다.
	 */
	@Transactional(readOnly = true)
	public List<AvailableUnitResponse> availability(Long golfCourseId, FacilityType facilityType, Long facilityId,
			Long facilityRoomTypeId, LocalDateTime from, LocalDateTime to, Integer guestCount) {
		if (from == null || to == null)
			throw new IllegalArgumentException("조회 기간(from, to)이 필요합니다.");
		BigDecimal hours = FacilityPricing.bookingHours(from, to);
		int guests = guestCount == null ? 1 : guestCount;
		if (guests < 1)
			throw new IllegalArgumentException("이용 인원은 1명 이상이어야 합니다.");
		Specification<FacilityUnit> spec = Specification.allOf(Specs.eq("status", FacilityUnitStatus.AVAILABLE),
				Specs.eq("facility.active", true), Specs.eq("roomType.active", true),
				Specs.eq("facility.golfCourseId", golfCourseId), Specs.eq("facility.type", facilityType),
				Specs.eq("facility.id", facilityId), Specs.eq("roomType.id", facilityRoomTypeId),
				Specs.gte("capacity", guests), noActiveBookingBetween(from, to));
		return units.findAll(spec, Sort.by("facility.id", "unitCode")).stream().map(u -> {
			FacilityRoomType t = u.getRoomType();
			BigDecimal rate = t.getHourlyBaseRate();
			BigDecimal estimate = rate.multiply(hours).setScale(2, RoundingMode.HALF_UP);
			return new AvailableUnitResponse(UnitResponse.of(u), t.getId(), t.getName(), rate, hours, estimate);
		}).toList();
	}

	/** 시설 한 곳의 공간별 점유 예약(from~to 포함, 최대 {@value #MAX_CALENDAR_DAYS}일) */
	@Transactional(readOnly = true)
	public CalendarResponse calendar(long facilityId, LocalDate from, LocalDate to) {
		if (to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일이 시작일보다 앞설 수 없습니다.");
		if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS)
			throw new IllegalArgumentException("달력 조회 기간은 최대 " + MAX_CALENDAR_DAYS + "일입니다.");
		Facility f = facilities.findById(facilityId).orElseThrow(() -> NotFoundException.of("시설", facilityId));
		Map<Long, List<CalendarBooking>> byUnit = new HashMap<>();
		for (FacilityBooking b : repo.findForCalendar(facilityId, FacilityBookingStatus.ACTIVE, from.atStartOfDay(),
				to.plusDays(1).atStartOfDay()))
			byUnit.computeIfAbsent(b.getUnit().getId(), k -> new ArrayList<>()).add(CalendarBooking.of(b));
		List<CalendarUnit> unitRows = units.findByFacilityId(facilityId).stream()
				.map(u -> new CalendarUnit(u.getId(), u.getUnitCode(), u.getUnitName(), u.getRoomType().getId(),
						u.getRoomType().getName(), u.getCapacity(), u.getStatus(),
						byUnit.getOrDefault(u.getId(), List.of())))
				.toList();
		return new CalendarResponse(f.getId(), f.getCode(), f.getName(), f.getType(), f.isActive(), from, to,
				unitRows);
	}

	// ---------------------------------------------------------------- 등록·수정

	/**
	 * 예약 등록. 공간을 잠근 뒤 시설 활성·공간 종류 사용 중·공간 AVAILABLE·수용 인원·기간 겹침을 확인한다.
	 * 시간당 요금은 공간 종류의 현재 시간당 기본요금을 스냅샷한다. 번호는 FB-yyyyMMdd-000123.
	 */
	public BookingResponse create(BookingCreateRequest r) {
		refs.requireIfPresent(Ref.MEMBER, r.memberId());
		LocalDateTime now = now();
		FacilityPricing.bookingHours(r.startAt(), r.endAt()); // 15분 단위·기간 검증(400)을 잠금 전에 먼저
		if (!r.endAt().isAfter(now))
			throw new IllegalArgumentException("이미 지난 기간은 예약할 수 없습니다.");
		FacilityUnit u = lockUnit(r.facilityUnitId());
		u.requireBookable();
		int guestCount = r.guestCount() == null ? 1 : r.guestCount();
		requireCapacity(u, guestCount);
		requireNoOverlap(u.getId(), r.startAt(), r.endAt(), 0L);
		Guest guest = guest(r.guestName(), r.guestPhone(), r.memberId());

		FacilityBookingStatus initial = Boolean.TRUE.equals(r.tentative()) ? FacilityBookingStatus.PENDING
				: FacilityBookingStatus.CONFIRMED;
		FacilityBooking b = new FacilityBooking(DocumentNo.temporary(), u, r.memberId(), initial,
				u.getRoomType().getHourlyBaseRate(), CurrentUser.idOrNull(), now);
		b.changeDetails(guest.name(), guest.phone(), r.startAt(), r.endAt(), guestCount, r.discountAmount(),
				blankToNull(r.specialRequest()), now);
		repo.saveAndFlush(b);
		b.assignNo(DocumentNo.of("FB", now.toLocalDate(), b.getId()));
		return BookingResponse.of(b);
	}

	/**
	 * 손님·기간·인원·할인·요청 사항 수정(입실 전만). 기간이 바뀌면 시설·공간 종류·공간 상태와 겹침을 다시 확인한다.
	 * 시간당 요금은 등록 때 스냅샷을 유지하고 이용 시간·기본 금액·합계를 다시 계산한다.
	 */
	public BookingResponse update(long id, BookingUpdateRequest r) {
		FacilityPricing.bookingHours(r.startAt(), r.endAt());
		Long unitId = repo.findUnitIdById(id).orElseThrow(() -> NotFoundException.of("시설 예약", id));
		FacilityUnit u = lockUnit(unitId);
		FacilityBooking b = lock(id);
		b.requireEditable();
		LocalDateTime now = now();
		boolean periodChanged = !r.startAt().equals(b.getStartAt()) || !r.endAt().equals(b.getEndAt());
		if (periodChanged) {
			if (!r.endAt().isAfter(now))
				throw new IllegalArgumentException("이미 지난 기간으로 바꿀 수 없습니다.");
			u.requireBookable();
			requireNoOverlap(u.getId(), r.startAt(), r.endAt(), id);
		}
		requireCapacity(u, r.guestCount());
		Guest guest = guest(r.guestName(), r.guestPhone(), b.getMemberId());
		b.changeDetails(guest.name(), guest.phone(), r.startAt(), r.endAt(), r.guestCount(), r.discountAmount(),
				blankToNull(r.specialRequest()), now);
		return BookingResponse.of(b);
	}

	// ---------------------------------------------------------------- 상태 변경

	public BookingResponse confirm(long id) {
		FacilityBooking b = lock(id);
		b.confirm(now());
		return BookingResponse.of(b);
	}

	public BookingResponse checkIn(long id) {
		FacilityBooking b = lock(id);
		b.checkIn(now());
		return BookingResponse.of(b);
	}

	public BookingResponse checkOut(long id) {
		FacilityBooking b = lock(id);
		b.checkOut(now());
		return BookingResponse.of(b);
	}

	public BookingResponse cancel(long id, String reason) {
		FacilityBooking b = lock(id);
		FacilityBookingStatus before = b.getStatus();
		String why = blankToNull(reason);
		b.cancel(why, now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("bookingNo", b.getBookingNo());
		details.put("before", before);
		details.put("paymentStatus", b.getPaymentStatus());
		if (why != null)
			details.put("reason", why);
		audit.log("CANCEL", "facility_booking", id, details);
		return BookingResponse.of(b);
	}

	public BookingResponse noShow(long id) {
		FacilityBooking b = lock(id);
		b.markNoShow(now());
		audit.log("NO_SHOW", "facility_booking", id,
				Map.of("bookingNo", b.getBookingNo(), "paymentStatus", b.getPaymentStatus()));
		return BookingResponse.of(b);
	}

	/** 수납 상태 기록(결제 테이블 없음). 같은 값이면 아무것도 하지 않는다. */
	public BookingResponse changePaymentStatus(long id, FacilityPaymentStatus paymentStatus, String memo) {
		FacilityBooking b = lock(id);
		FacilityPaymentStatus before = b.getPaymentStatus();
		if (before != paymentStatus) {
			b.changePaymentStatus(paymentStatus, now());
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("bookingNo", b.getBookingNo());
			details.put("before", before);
			details.put("after", paymentStatus);
			details.put("totalAmount", b.getTotalAmount());
			String m = blankToNull(memo);
			if (m != null)
				details.put("memo", m);
			audit.log("PAYMENT_STATUS_CHANGE", "facility_booking", id, details);
		}
		return BookingResponse.of(b);
	}

	// ---------------------------------------------------------------- 내부

	private record Guest(String name, String phone) {
	}

	/** 이름·전화가 비어 있으면 회원 정보로 채운다. 회원 예약이 아니면 이름은 필수(400). */
	private Guest guest(String name, String phone, Long memberId) {
		String n = blankToNull(name);
		String p = blankToNull(phone);
		if (memberId != null && (n == null || p == null)) {
			FacilityLookup.Contact c = lookup.member(memberId)
					.orElseThrow(() -> NotFoundException.of(Ref.MEMBER.label, memberId));
			if (n == null)
				n = c.name();
			if (p == null)
				p = c.phone();
		}
		if (n == null)
			throw new IllegalArgumentException("손님 이름(guestName)이 필요합니다. 회원 예약이면 memberId 를 주세요.");
		return new Guest(n, p);
	}

	private static void requireCapacity(FacilityUnit u, int guestCount) {
		if (guestCount > u.getCapacity())
			throw new IllegalArgumentException(
					"이용 인원(" + guestCount + "명)이 수용 인원(" + u.getCapacity() + "명)을 넘습니다: " + u.getUnitName());
	}

	/** 공간 행을 잠근 상태에서 호출해야 한다. */
	private void requireNoOverlap(long unitId, LocalDateTime start, LocalDateTime end, long excludeId) {
		List<FacilityBooking> overlaps = repo.findOverlapping(unitId, FacilityBookingStatus.ACTIVE, start, end,
				excludeId);
		if (!overlaps.isEmpty()) {
			FacilityBooking o = overlaps.get(0);
			throw new ConflictException("같은 공간에 겹치는 예약이 있습니다: " + o.getBookingNo() + " (" + o.getStartAt() + " ~ "
					+ o.getEndAt() + ")");
		}
	}

	/** 기간 [from, to) 와 겹치는 점유 예약이 없는 공간 */
	private static Specification<FacilityUnit> noActiveBookingBetween(LocalDateTime from, LocalDateTime to) {
		return (root, q, cb) -> {
			Subquery<Long> sq = q.subquery(Long.class);
			Root<FacilityBooking> b = sq.from(FacilityBooking.class);
			sq.select(b.<Long>get("id")).where(cb.equal(b.get("unit"), root),
					b.get("status").in(FacilityBookingStatus.ACTIVE),
					cb.lessThan(b.<LocalDateTime>get("startAt"), to),
					cb.greaterThan(b.<LocalDateTime>get("endAt"), from));
			return cb.not(cb.exists(sq));
		};
	}

	private static Specification<FacilityBooking> endsAfter(LocalDateTime at) {
		return (root, q, cb) -> cb.greaterThan(root.<LocalDateTime>get("endAt"), at);
	}

	private FacilityBooking lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("시설 예약", id));
	}

	private FacilityUnit lockUnit(long id) {
		return units.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("공간", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return FacilityService.blankToNull(s);
	}
}
