package com.golf.erp.round;

import com.golf.erp.billing.BillingService;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.round.RoundDtos.AssignmentResponse;
import com.golf.erp.round.RoundDtos.BoardItem;
import com.golf.erp.round.RoundDtos.BoardResponse;
import com.golf.erp.round.RoundDtos.EventResponse;
import com.golf.erp.round.RoundDtos.PlayerResponse;
import com.golf.erp.round.RoundDtos.RoundCourseResponse;
import com.golf.erp.round.RoundDtos.RoundDetail;
import com.golf.erp.round.RoundDtos.RoundSummary;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 라운드 조회(목록·상세·현황판·이용 내역) */
@Service
@Transactional(readOnly = true)
public class RoundQueryService {
	private final GolfRoundRepository rounds;
	private final RoundPlayerRepository players;
	private final RoundCourseRepository roundCourses;
	private final RoundAssignmentRepository assignments;
	private final RoundEventRepository events;
	private final RoundLookup lookup;
	private final BillingService billing;
	private final Clock clock;

	public RoundQueryService(GolfRoundRepository rounds, RoundPlayerRepository players,
			RoundCourseRepository roundCourses, RoundAssignmentRepository assignments, RoundEventRepository events,
			RoundLookup lookup, BillingService billing, Clock clock) {
		this.rounds = rounds;
		this.players = players;
		this.roundCourses = roundCourses;
		this.assignments = assignments;
		this.events = events;
		this.lookup = lookup;
		this.billing = billing;
		this.clock = clock;
	}

	/** @param keyword 참가자 이름 또는 라운드 번호(부분 일치) */
	public PageResult<RoundSummary> list(Long golfCourseId, LocalDate date, RoundStatus status, String keyword,
			int page, int size) {
		Specification<GolfRound> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("playDate", date), Specs.eq("status", status), keyword(keyword));
		Page<GolfRound> result = rounds.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "playDate")
				.and(Sort.by("teeOffTime", "checkedInAt")).and(Sort.by(Sort.Direction.DESC, "id"))));
		List<Long> ids = result.getContent().stream().map(GolfRound::getId).toList();
		Map<Long, List<RoundPlayer>> byRound = playersByRound(ids);
		Map<Long, String> courseNames = lookup.courseNames(result.getContent().stream()
				.map(GolfRound::getCurrentCourseId).filter(Objects::nonNull).collect(Collectors.toSet()));
		return PageResult.of(result,
				r -> RoundSummary.of(r, byRound.getOrDefault(r.getId(), List.of()), courseNames));
	}

	public RoundDetail detail(long roundId) {
		GolfRound r = rounds.findById(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
		List<RoundCourse> courses = roundCourses.findByRoundIdOrderBySeqNo(roundId);
		Set<Long> courseIds = courses.stream().map(RoundCourse::getCourseId).collect(Collectors.toCollection(HashSet::new));
		if (r.getCurrentCourseId() != null)
			courseIds.add(r.getCurrentCourseId());
		Map<Long, String> courseNames = lookup.courseNames(courseIds);
		List<RoundAssignment> assigned = assignments.findByRoundIdOrderByAssignedAtAscIdAsc(roundId);
		Map<Long, String> caddies = lookup.caddieLabels(ids(assigned, ResourceType.CADDIE));
		Map<Long, String> carts = lookup.cartLabels(ids(assigned, ResourceType.CART));
		return new RoundDetail(r.getId(), r.getRoundNo(), r.getGolfCourseId(), r.getReservationId(), r.getTeeTimeId(),
				r.getPlayDate(), r.getTeeOffTime(), r.getPlannedHoles(), r.getStatus(), r.getCurrentCourseId(),
				r.getCurrentCourseId() == null ? null : courseNames.get(r.getCurrentCourseId()), r.getCheckedInAt(),
				r.getStartedAt(), r.getFinishedAt(), r.getCancelledAt(), r.getCancelReason(), r.getSettlementStatus(),
				r.getMemo(), r.getCreatedBy(), r.getVersion(), r.getCreatedAt(), r.getUpdatedAt(),
				players.findByRoundIdOrderByPlayerOrder(roundId).stream().map(PlayerResponse::of).toList(),
				courses.stream().map(c -> RoundCourseResponse.of(c, courseNames)).toList(),
				assigned.stream().map(a -> AssignmentResponse.of(a, label(a, caddies, carts))).toList(),
				billing.summary(roundId));
	}

	/** 그날(기본 오늘) 골프장의 취소되지 않은 라운드 현황. 예정 출발 시각 순 */
	public BoardResponse board(long golfCourseId, LocalDate date) {
		LocalDate day = date == null ? LocalDate.now(clock) : date;
		List<GolfRound> list = rounds.findAll(
				Specification.allOf(Specs.eq("golfCourseId", golfCourseId), Specs.eq("playDate", day),
						(root, q, cb) -> cb.notEqual(root.get("status"), RoundStatus.CANCELLED)),
				Sort.by("teeOffTime", "checkedInAt", "id"));
		List<Long> ids = list.stream().map(GolfRound::getId).toList();
		Map<Long, List<RoundPlayer>> byRound = playersByRound(ids);
		List<RoundAssignment> active = ids.isEmpty() ? List.of() : assignments.findByRoundIdInAndReleasedAtIsNull(ids);
		Map<Long, String> caddies = lookup.caddieLabels(ids(active, ResourceType.CADDIE));
		Map<Long, String> carts = lookup.cartLabels(ids(active, ResourceType.CART));
		Map<Long, List<RoundAssignment>> activeByRound = active.stream()
				.collect(Collectors.groupingBy(RoundAssignment::getRoundId));
		Map<Long, String> courseNames = lookup.courseNames(
				list.stream().map(GolfRound::getCurrentCourseId).filter(Objects::nonNull).collect(Collectors.toSet()));
		Map<Long, Short> openSeq = new HashMap<>();
		for (GolfRound r : list)
			if (r.getStatus() == RoundStatus.PLAYING)
				roundCourses.findByRoundIdAndFinishedAtIsNull(r.getId()).ifPresent(c -> openSeq.put(r.getId(), c.getSeqNo()));
		LocalDateTime now = LocalDateTime.now(clock);

		List<BoardItem> items = list.stream().map(r -> {
			List<RoundPlayer> ps = byRound.getOrDefault(r.getId(), List.of());
			List<RoundAssignment> as = activeByRound.getOrDefault(r.getId(), List.of());
			Long elapsed = r.getStartedAt() == null ? null
					: Duration.between(r.getStartedAt(), r.getFinishedAt() == null ? now : r.getFinishedAt()).toMinutes();
			return new BoardItem(r.getId(), r.getRoundNo(), r.getTeeOffTime(), r.getPlannedHoles(), r.getStatus(),
					r.getCurrentCourseId(), r.getCurrentCourseId() == null ? null : courseNames.get(r.getCurrentCourseId()),
					openSeq.get(r.getId()), r.getCheckedInAt(), r.getStartedAt(), r.getFinishedAt(), elapsed,
					ps.stream().map(RoundPlayer::getName).toList(),
					(int) ps.stream().filter(p -> !p.isCheckedOut()).count(),
					as.stream().filter(a -> a.getResourceType() == ResourceType.CADDIE)
							.map(a -> label(a, caddies, carts)).toList(),
					as.stream().filter(a -> a.getResourceType() == ResourceType.CART)
							.map(a -> label(a, caddies, carts)).toList(),
					r.getSettlementStatus());
		}).toList();
		return new BoardResponse(golfCourseId, day, count(list, RoundStatus.CHECKED_IN), count(list, RoundStatus.PLAYING),
				count(list, RoundStatus.FINISHED), items);
	}

	public List<EventResponse> events(long roundId) {
		if (!rounds.existsById(roundId))
			throw NotFoundException.of("라운드", roundId);
		return events.findByRoundIdOrderByOccurredAtAscIdAsc(roundId).stream().map(EventResponse::of).toList();
	}

	/** 배정 한 건의 응답(라벨 포함) */
	AssignmentResponse assignment(RoundAssignment a) {
		String label = a.getResourceType() == ResourceType.CADDIE
				? lookup.caddieLabels(Set.of(a.getCaddieId())).get(a.getCaddieId())
				: lookup.cartLabels(Set.of(a.getCartId())).get(a.getCartId());
		return AssignmentResponse.of(a, label);
	}

	private Map<Long, List<RoundPlayer>> playersByRound(List<Long> roundIds) {
		if (roundIds.isEmpty())
			return Map.of();
		return players.findByRoundIdInOrderByRoundIdAscPlayerOrderAsc(roundIds).stream()
				.collect(Collectors.groupingBy(RoundPlayer::getRoundId));
	}

	private static Set<Long> ids(List<RoundAssignment> list, ResourceType type) {
		return list.stream().filter(a -> a.getResourceType() == type).map(RoundAssignment::getResourceId)
				.collect(Collectors.toSet());
	}

	private static String label(RoundAssignment a, Map<Long, String> caddies, Map<Long, String> carts) {
		return a.getResourceType() == ResourceType.CADDIE ? caddies.get(a.getCaddieId()) : carts.get(a.getCartId());
	}

	private static int count(List<GolfRound> list, RoundStatus status) {
		return (int) list.stream().filter(r -> r.getStatus() == status).count();
	}

	/** 참가자 이름 또는 라운드 번호 부분 일치(대소문자 무시) */
	private static Specification<GolfRound> keyword(String keyword) {
		if (keyword == null || keyword.isBlank())
			return null;
		String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
				.replace("_", "\\_") + "%";
		return (root, q, cb) -> {
			Subquery<Long> sq = q.subquery(Long.class);
			Root<RoundPlayer> p = sq.from(RoundPlayer.class);
			sq.select(p.get("id")).where(cb.equal(p.get("roundId"), root.get("id")),
					cb.like(cb.lower(p.get("name")), pattern, '\\'));
			return cb.or(cb.exists(sq), cb.like(cb.lower(root.get("roundNo")), pattern, '\\'));
		};
	}
}
