package com.golf.erp.round;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.round.RoundDtos.AssignmentResponse;
import com.golf.erp.round.RoundLookup.ResourceInfo;
import com.golf.erp.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 캐디·카트 배정. 체크인·플레이 중(CHECKED_IN·PLAYING) 라운드에만 배정·해제·교체할 수 있다.
 * 근무 중(caddie_status / cart_status = ACTIVE)이고 지금 다른 라운드에 배정되지 않은 것만 배정한다.
 * 동시에 같은 캐디·카트를 두 라운드에 넣는 경합은 부분 유니크 인덱스(ux_assignment_active_*)가 최종 방어선이다.
 */
@Service
@Transactional
public class AssignmentService {
	private static final String DEFAULT_RELEASE_REASON = "배정 해제";

	private final GolfRoundRepository rounds;
	private final RoundAssignmentRepository repo;
	private final RoundLookup lookup;
	private final RoundEventRecorder events;
	private final RoundQueryService queries;
	private final Clock clock;

	public AssignmentService(GolfRoundRepository rounds, RoundAssignmentRepository repo, RoundLookup lookup,
			RoundEventRecorder events, RoundQueryService queries, Clock clock) {
		this.rounds = rounds;
		this.repo = repo;
		this.lookup = lookup;
		this.events = events;
		this.queries = queries;
		this.clock = clock;
	}

	public AssignmentResponse assign(long roundId, ResourceType type, long resourceId) {
		GolfRound round = lockActive(roundId);
		ResourceInfo res = requireAssignable(round, type, resourceId);
		LocalDateTime now = now();
		RoundAssignment a = save(RoundAssignment.of(roundId, type, resourceId, CurrentUser.idOrNull(), now), type);
		events.record(roundId, assignedEvent(type), label(type) + " " + res.label() + " 배정");
		round.touch(now);
		return AssignmentResponse.of(a, res.label());
	}

	public AssignmentResponse release(long roundId, long assignmentId, String reason) {
		GolfRound round = lockActive(roundId);
		RoundAssignment a = findActive(roundId, assignmentId);
		LocalDateTime now = now();
		String why = reason == null || reason.isBlank() ? DEFAULT_RELEASE_REASON : reason.trim();
		a.release(why, now);
		events.record(roundId, releasedEvent(a.getResourceType()),
				label(a.getResourceType()) + " " + resourceLabel(a) + " 해제(" + why + ")");
		round.touch(now);
		return queries.assignment(a);
	}

	/** 같은 종류의 다른 캐디·카트로 교체: 기존 배정을 해제하고 새로 배정한다. */
	public AssignmentResponse swap(long roundId, long assignmentId, long newResourceId, String reason) {
		GolfRound round = lockActive(roundId);
		RoundAssignment old = findActive(roundId, assignmentId);
		ResourceType type = old.getResourceType();
		if (old.getResourceId().equals(newResourceId))
			throw new IllegalArgumentException("지금 배정된 " + label(type) + "와(과) 같습니다.");
		ResourceInfo res = requireAssignable(round, type, newResourceId);
		LocalDateTime now = now();
		String why = reason == null || reason.isBlank() ? label(type) + " 교체" : reason.trim();
		String oldLabel = resourceLabel(old);
		old.release(why, now);
		repo.flush();
		RoundAssignment a = save(RoundAssignment.of(roundId, type, newResourceId, CurrentUser.idOrNull(), now), type);
		events.record(roundId, releasedEvent(type), label(type) + " " + oldLabel + " 해제(" + why + ")");
		events.record(roundId, assignedEvent(type), label(type) + " " + res.label() + " 배정(교체)");
		round.touch(now);
		return AssignmentResponse.of(a, res.label());
	}

	/** 라운드 종료·취소 때 남은 배정을 모두 해제한다(호출자가 라운드를 잠근 상태). */
	void releaseAll(GolfRound round, String reason, LocalDateTime now) {
		for (RoundAssignment a : repo.findByRoundIdAndReleasedAtIsNull(round.getId())) {
			a.release(reason, now);
			events.record(round.getId(), releasedEvent(a.getResourceType()),
					label(a.getResourceType()) + " " + resourceLabel(a) + " 해제(" + reason + ")");
		}
	}

	// ── 내부 ──

	private ResourceInfo requireAssignable(GolfRound round, ResourceType type, long resourceId) {
		ResourceInfo res = lookup.resource(type, resourceId)
				.orElseThrow(() -> NotFoundException.of(label(type), resourceId));
		if (res.golfCourseId() != round.getGolfCourseId())
			throw new IllegalArgumentException("다른 골프장 소속 " + label(type) + "입니다: " + res.label());
		if (!"ACTIVE".equals(res.status()))
			throw new ConflictException(type == ResourceType.CADDIE
					? "근무 중인 캐디가 아닙니다: " + res.label() + "(" + res.status() + ")"
					: "운행할 수 없는 카트입니다: " + res.label() + "(" + res.status() + ")");
		Optional<RoundAssignment> busy = type == ResourceType.CADDIE ? repo.findFirstByCaddieIdAndReleasedAtIsNull(resourceId)
				: repo.findFirstByCartIdAndReleasedAtIsNull(resourceId);
		busy.ifPresent(a -> {
			if (a.getRoundId().equals(round.getId()))
				throw new ConflictException("이미 이 라운드에 배정된 " + label(type) + "입니다: " + res.label());
			String other = rounds.findById(a.getRoundId()).map(GolfRound::getRoundNo).orElse(String.valueOf(a.getRoundId()));
			throw new ConflictException("다른 라운드(" + other + ")에 배정 중인 " + label(type) + "입니다: " + res.label());
		});
		return res;
	}

	/** 저장 후 바로 flush 해, 동시에 들어온 같은 자원 배정(부분 유니크 인덱스 위반)을 친절한 409 로 바꾼다. */
	private RoundAssignment save(RoundAssignment a, ResourceType type) {
		try {
			return repo.saveAndFlush(a);
		} catch (DataIntegrityViolationException e) {
			throw new ConflictException("방금 다른 라운드에 배정된 " + label(type) + "입니다. 다시 조회한 뒤 시도하세요.");
		}
	}

	private RoundAssignment findActive(long roundId, long assignmentId) {
		RoundAssignment a = repo.findById(assignmentId).filter(x -> x.getRoundId().equals(roundId))
				.orElseThrow(() -> NotFoundException.of("배정", assignmentId));
		if (!a.isActive())
			throw new ConflictException("이미 해제된 배정입니다.");
		return a;
	}

	private String resourceLabel(RoundAssignment a) {
		return lookup.resource(a.getResourceType(), a.getResourceId()).map(ResourceInfo::label)
				.orElse(String.valueOf(a.getResourceId()));
	}

	private GolfRound lockActive(long roundId) {
		GolfRound round = rounds.findByIdForUpdate(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
		round.requireActive();
		return round;
	}

	private static String label(ResourceType type) {
		return type == ResourceType.CADDIE ? "캐디" : "카트";
	}

	private static RoundEventType assignedEvent(ResourceType type) {
		return type == ResourceType.CADDIE ? RoundEventType.CADDIE_ASSIGNED : RoundEventType.CART_ASSIGNED;
	}

	private static RoundEventType releasedEvent(ResourceType type) {
		return type == ResourceType.CADDIE ? RoundEventType.CADDIE_RELEASED : RoundEventType.CART_RELEASED;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
