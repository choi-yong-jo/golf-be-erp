package com.golf.erp.round;

import com.golf.erp.billing.BillingDtos.BillSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** 라운드(체크인·진행·참가자·배정·스코어·이용 내역) API 요청/응답 */
public final class RoundDtos {
	private RoundDtos() {
	}

	// ── 요청 ──

	/**
	 * 체크인. reservationId 가 있으면 예약에서 골프장·티타임·일시·참가자를 가져온다(players 를 주면 그것을 우선).
	 * 워크인이면 golfCourseId 와 players 가 필요하다. playDate 기본 오늘, plannedHoles 기본(티타임 홀 수 또는 18).
	 */
	public record CheckInRequest(Long reservationId, Long golfCourseId, Long teeTimeId, LocalDate playDate,
			LocalTime teeOffTime, Short plannedHoles, @Size(max = 500) String memo,
			@Size(max = 8) List<@Valid @NotNull PlayerRequest> players) {
	}

	/** 회원이면 memberId(이름·전화는 비우면 회원 정보), 비회원이면 name 필수 */
	public record PlayerRequest(Long memberId, @Size(max = 100) String name, @Size(max = 30) String phone,
			@Min(0) @Max(54) Short handicap, @Size(max = 10) String lockerNo) {
	}

	public record PlayerUpdateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 30) String phone,
			@Min(0) @Max(54) Short handicap, @Size(max = 10) String lockerNo) {
	}

	public record CourseRequest(@NotNull Long courseId) {
	}

	public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
	}

	/** resourceId: 캐디 id 또는 카트 id */
	public record AssignRequest(@NotNull ResourceType resourceType, @NotNull Long resourceId) {
	}

	public record ReleaseRequest(@Size(max = 200) String reason) {
	}

	/** 같은 종류의 다른 캐디·카트로 교체 */
	public record SwapRequest(@NotNull Long resourceId, @Size(max = 200) String reason) {
	}

	/**
	 * 한 홀의 스코어(여러 참가자 한 번에). roundCourseId 를 비우면 지금 사용 중인 코스 구간.
	 * holeId 는 그 구간 코스의 홀이어야 한다. 이미 있으면 고친다.
	 */
	public record ScoreSaveRequest(Long roundCourseId, @NotNull Long holeId,
			@NotEmpty @Size(max = 8) List<@Valid @NotNull ScoreEntry> entries) {
	}

	public record ScoreEntry(@NotNull Long roundPlayerId, @NotNull @Min(1) @Max(20) Short strokes,
			@Min(0) @Max(10) Short putts, @Min(0) @Max(10) Short penalties, Boolean fairwayHit) {
	}

	// ── 응답 ──

	public record PlayerResponse(Long id, Short playerOrder, Long memberId, String name, String phone, Short handicap,
			String lockerNo, LocalDateTime checkedInAt, LocalDateTime checkedOutAt, Short holesPlayed,
			Short totalStrokes, Short totalPutts, Short parPlayed, Integer scoreToPar) {

		static PlayerResponse of(RoundPlayer p) {
			return new PlayerResponse(p.getId(), p.getPlayerOrder(), p.getMemberId(), p.getName(), p.getPhone(),
					p.getHandicap(), p.getLockerNo(), p.getCheckedInAt(), p.getCheckedOutAt(), p.getHolesPlayed(),
					p.getTotalStrokes(), p.getTotalPutts(), p.getParPlayed(), p.getScoreToPar());
		}
	}

	public record RoundCourseResponse(Long id, Short seqNo, Long courseId, String courseName, LocalDateTime startedAt,
			LocalDateTime finishedAt) {

		static RoundCourseResponse of(RoundCourse c, Map<Long, String> courseNames) {
			return new RoundCourseResponse(c.getId(), c.getSeqNo(), c.getCourseId(), courseNames.get(c.getCourseId()),
					c.getStartedAt(), c.getFinishedAt());
		}
	}

	/** label: 캐디 "번호 이름", 카트 "번호" */
	public record AssignmentResponse(Long id, Long roundId, ResourceType resourceType, Long resourceId, String label,
			boolean active, LocalDateTime assignedAt, LocalDateTime releasedAt, Long assignedBy,
			String releaseReason) {

		static AssignmentResponse of(RoundAssignment a, String label) {
			return new AssignmentResponse(a.getId(), a.getRoundId(), a.getResourceType(), a.getResourceId(), label,
					a.isActive(), a.getAssignedAt(), a.getReleasedAt(), a.getAssignedBy(), a.getReleaseReason());
		}
	}

	/** 목록용 */
	public record RoundSummary(Long id, String roundNo, Long golfCourseId, Long reservationId, LocalDate playDate,
			LocalTime teeOffTime, Short plannedHoles, RoundStatus status, Long currentCourseId,
			String currentCourseName, RoundSettlementStatus settlementStatus, int playerCount,
			List<String> playerNames, LocalDateTime checkedInAt, LocalDateTime startedAt, LocalDateTime finishedAt) {

		static RoundSummary of(GolfRound r, List<RoundPlayer> players, Map<Long, String> courseNames) {
			return new RoundSummary(r.getId(), r.getRoundNo(), r.getGolfCourseId(), r.getReservationId(),
					r.getPlayDate(), r.getTeeOffTime(), r.getPlannedHoles(), r.getStatus(), r.getCurrentCourseId(),
					r.getCurrentCourseId() == null ? null : courseNames.get(r.getCurrentCourseId()),
					r.getSettlementStatus(), players.size(), players.stream().map(RoundPlayer::getName).toList(),
					r.getCheckedInAt(), r.getStartedAt(), r.getFinishedAt());
		}
	}

	/** 상세. billing: 요금·수납 합계(취소 제외) */
	public record RoundDetail(Long id, String roundNo, Long golfCourseId, Long reservationId, Long teeTimeId,
			LocalDate playDate, LocalTime teeOffTime, Short plannedHoles, RoundStatus status, Long currentCourseId,
			String currentCourseName, LocalDateTime checkedInAt, LocalDateTime startedAt, LocalDateTime finishedAt,
			LocalDateTime cancelledAt, String cancelReason, RoundSettlementStatus settlementStatus, String memo,
			Long createdBy, Long version, LocalDateTime createdAt, LocalDateTime updatedAt,
			List<PlayerResponse> players, List<RoundCourseResponse> courses, List<AssignmentResponse> assignments,
			BillSummary billing) {
	}

	/** 오늘의 라운드 현황판 한 줄. elapsedMinutes: 출발 후 경과 분(출발 전이면 null) */
	public record BoardItem(Long roundId, String roundNo, LocalTime teeOffTime, Short plannedHoles,
			RoundStatus status, Long currentCourseId, String currentCourseName, Short currentSeqNo,
			LocalDateTime checkedInAt, LocalDateTime startedAt, LocalDateTime finishedAt, Long elapsedMinutes,
			List<String> playerNames, int activePlayerCount, List<String> caddies, List<String> carts,
			RoundSettlementStatus settlementStatus) {
	}

	public record BoardResponse(Long golfCourseId, LocalDate date, int checkedInCount, int playingCount,
			int finishedCount, List<BoardItem> rounds) {
	}

	public record EventResponse(Long id, RoundEventType type, Long courseId, Long roundPlayerId, String message,
			Long actorAdminUserId, LocalDateTime occurredAt) {

		static EventResponse of(RoundEvent e) {
			return new EventResponse(e.getId(), e.getType(), e.getCourseId(), e.getRoundPlayerId(), e.getMessage(),
					e.getActorAdminUserId(), e.getOccurredAt());
		}
	}

	// ── 스코어카드 ──

	public record HoleResponse(Long holeId, Short holeNo, Short par, Short handicap) {
	}

	/** 코스 사용 구간 하나(예: 1구간 OUT 9홀). par: 그 코스 홀들의 파 합계 */
	public record ScorecardSegment(Long roundCourseId, Short seqNo, Long courseId, String courseName, int par,
			List<HoleResponse> holes) {
	}

	public record ScoreResponse(Long scoreId, Long roundCourseId, Long holeId, Short strokes, Short putts,
			Short penalties, Boolean fairwayHit) {

		static ScoreResponse of(Score s) {
			return new ScoreResponse(s.getId(), s.getRoundCourseId(), s.getHoleId(), s.getStrokes(), s.getPutts(),
					s.getPenalties(), s.getFairwayHit());
		}
	}

	public record PlayerScorecard(Long roundPlayerId, Short playerOrder, String name, Short holesPlayed,
			Short totalStrokes, Short totalPutts, Short parPlayed, Integer scoreToPar, List<ScoreResponse> scores) {
	}

	public record Scorecard(Long roundId, String roundNo, RoundStatus status, List<ScorecardSegment> segments,
			List<PlayerScorecard> players) {
	}
}
