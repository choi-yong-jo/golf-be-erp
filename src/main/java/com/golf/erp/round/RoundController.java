package com.golf.erp.round;

import com.golf.erp.common.PageResult;
import com.golf.erp.round.RoundDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "라운드")
@RestController
@RequestMapping("/api/rounds")
public class RoundController {
	private final RoundService rounds;
	private final RoundQueryService queries;
	private final AssignmentService assignments;
	private final ScoreService scores;

	public RoundController(RoundService rounds, RoundQueryService queries, AssignmentService assignments,
			ScoreService scores) {
		this.rounds = rounds;
		this.queries = queries;
		this.assignments = assignments;
		this.scores = scores;
	}

	// ── 조회 ──

	@Operation(summary = "라운드 목록", description = "date: 플레이 날짜, keyword: 참가자 이름 또는 라운드 번호")
	@GetMapping
	public PageResult<RoundSummary> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) LocalDate date, @RequestParam(required = false) RoundStatus status,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return queries.list(golfCourseId, date, status, keyword, page, size);
	}

	@Operation(summary = "라운드 현황판", description = "그날(기본 오늘) 골프장의 취소되지 않은 라운드. 현재 코스·경과 시간·배정 중인 캐디/카트")
	@GetMapping("/board")
	public BoardResponse board(@RequestParam long golfCourseId, @RequestParam(required = false) LocalDate date) {
		return queries.board(golfCourseId, date);
	}

	@Operation(summary = "라운드 상세", description = "참가자, 코스 사용 구간, 배정 이력(현재·과거), 요금·수납 합계")
	@GetMapping("/{id}")
	public RoundDetail get(@PathVariable long id) {
		return queries.detail(id);
	}

	@Operation(summary = "라운드 이용 내역(타임라인)")
	@GetMapping("/{id}/events")
	public List<EventResponse> events(@PathVariable long id) {
		return queries.events(id);
	}

	// ── 진행 ──

	@Operation(summary = "체크인", description = "워크인(golfCourseId·players) 또는 예약(reservationId). 참가자별 그린피 자동 부과, "
			+ "예약 선결제는 PREPAID 수납으로 기록. 일 마감된 날이면 409")
	@PostMapping("/check-in")
	@ResponseStatus(HttpStatus.CREATED)
	public RoundDetail checkIn(@Valid @RequestBody CheckInRequest r) {
		return rounds.checkIn(r);
	}

	@Operation(summary = "출발", description = "CHECKED_IN → PLAYING, 첫 코스 사용 시작")
	@PostMapping("/{id}/start")
	public RoundDetail start(@PathVariable long id, @Valid @RequestBody CourseRequest r) {
		return rounds.start(id, r.courseId());
	}

	@Operation(summary = "코스 이동", description = "지금 코스 구간을 닫고 다음 코스 구간을 연다(최대 4구간)")
	@PostMapping("/{id}/change-course")
	public RoundDetail changeCourse(@PathVariable long id, @Valid @RequestBody CourseRequest r) {
		return rounds.changeCourse(id, r.courseId());
	}

	@Operation(summary = "종료", description = "PLAYING → FINISHED. 배정 해제, 참가자 체크아웃, 예약 이용 완료 처리")
	@PostMapping("/{id}/finish")
	public RoundDetail finish(@PathVariable long id) {
		return rounds.finish(id);
	}

	@Operation(summary = "취소", description = "종료 전만. 수납이 남아 있으면 409(먼저 수납 취소). 요금은 모두 취소된다")
	@PostMapping("/{id}/cancel")
	public RoundDetail cancel(@PathVariable long id, @Valid @RequestBody CancelRequest r) {
		return rounds.cancel(id, r.reason());
	}

	// ── 참가자 ──

	@Operation(summary = "참가자 추가", description = "최대 8명. 그린피 자동 부과(같은 라운드 그린피 단가)")
	@PostMapping("/{id}/players")
	@ResponseStatus(HttpStatus.CREATED)
	public PlayerResponse addPlayer(@PathVariable long id, @Valid @RequestBody PlayerRequest r) {
		return rounds.addPlayer(id, r);
	}

	@Operation(summary = "참가자 수정", description = "이름·전화·핸디캡·락커 번호")
	@PutMapping("/{id}/players/{playerId}")
	public PlayerResponse updatePlayer(@PathVariable long id, @PathVariable long playerId,
			@Valid @RequestBody PlayerUpdateRequest r) {
		return rounds.updatePlayer(id, playerId, r);
	}

	@Operation(summary = "참가자 제외", description = "스코어·수납이 있으면 409. 그 참가자의 요금은 취소된다")
	@DeleteMapping("/{id}/players/{playerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removePlayer(@PathVariable long id, @PathVariable long playerId) {
		rounds.removePlayer(id, playerId);
	}

	@Operation(summary = "참가자 개별 체크아웃")
	@PostMapping("/{id}/players/{playerId}/check-out")
	public PlayerResponse checkOutPlayer(@PathVariable long id, @PathVariable long playerId) {
		return rounds.checkOutPlayer(id, playerId);
	}

	// ── 캐디·카트 배정 ──

	@Operation(summary = "캐디·카트 배정", description = "근무 중(ACTIVE)이고 다른 라운드에 배정되지 않은 것만. 체크인·플레이 중 라운드만")
	@PostMapping("/{id}/assignments")
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentResponse assign(@PathVariable long id, @Valid @RequestBody AssignRequest r) {
		return assignments.assign(id, r.resourceType(), r.resourceId());
	}

	@Operation(summary = "배정 해제")
	@PostMapping("/{id}/assignments/{assignmentId}/release")
	public AssignmentResponse release(@PathVariable long id, @PathVariable long assignmentId,
			@Valid @RequestBody(required = false) ReleaseRequest r) {
		return assignments.release(id, assignmentId, r == null ? null : r.reason());
	}

	@Operation(summary = "배정 교체", description = "기존 배정을 해제하고 같은 종류의 다른 캐디·카트를 배정한다")
	@PostMapping("/{id}/assignments/{assignmentId}/swap")
	public AssignmentResponse swap(@PathVariable long id, @PathVariable long assignmentId,
			@Valid @RequestBody SwapRequest r) {
		return assignments.swap(id, assignmentId, r.resourceId(), r.reason());
	}

	// ── 스코어 ──

	@Operation(summary = "스코어카드", description = "코스 구간별 홀 정보와 참가자별 홀 스코어·합계")
	@GetMapping("/{id}/scorecard")
	public Scorecard scorecard(@PathVariable long id) {
		return scores.scorecard(id);
	}

	@Operation(summary = "홀 스코어 입력·수정", description = "한 홀의 여러 참가자 스코어. roundCourseId 를 비우면 지금 사용 중인 구간. "
			+ "PLAYING·FINISHED 라운드만. 저장 후 참가자 합계를 다시 계산한다")
	@PutMapping("/{id}/scores")
	public Scorecard saveScores(@PathVariable long id, @Valid @RequestBody ScoreSaveRequest r) {
		return scores.save(id, r);
	}
}
