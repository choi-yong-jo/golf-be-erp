package com.golf.erp.round;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.round.RoundDtos.HoleResponse;
import com.golf.erp.round.RoundDtos.PlayerScorecard;
import com.golf.erp.round.RoundDtos.ScoreEntry;
import com.golf.erp.round.RoundDtos.ScoreResponse;
import com.golf.erp.round.RoundDtos.ScoreSaveRequest;
import com.golf.erp.round.RoundDtos.Scorecard;
import com.golf.erp.round.RoundDtos.ScorecardSegment;
import com.golf.erp.round.RoundLookup.HoleInfo;
import com.golf.erp.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홀별 스코어와 스코어카드. 출발한 라운드(PLAYING)와 종료된 라운드(FINISHED, 정정용)에 입력할 수 있다.
 * 저장할 때마다 해당 참가자의 합계(친 홀 수·타수·퍼트·파 합계)를 score 전체로 다시 계산해 round_player 에 넣는다.
 */
@Service
@Transactional
public class ScoreService {
	private final GolfRoundRepository rounds;
	private final RoundPlayerRepository players;
	private final RoundCourseRepository roundCourses;
	private final ScoreRepository scores;
	private final RoundLookup lookup;
	private final RoundEventRecorder events;
	private final Clock clock;

	public ScoreService(GolfRoundRepository rounds, RoundPlayerRepository players, RoundCourseRepository roundCourses,
			ScoreRepository scores, RoundLookup lookup, RoundEventRecorder events, Clock clock) {
		this.rounds = rounds;
		this.players = players;
		this.roundCourses = roundCourses;
		this.scores = scores;
		this.lookup = lookup;
		this.events = events;
		this.clock = clock;
	}

	public Scorecard save(long roundId, ScoreSaveRequest r) {
		GolfRound round = rounds.findByIdForUpdate(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
		if (round.getStatus() != RoundStatus.PLAYING && round.getStatus() != RoundStatus.FINISHED)
			throw new ConflictException("출발한 라운드에만 스코어를 입력할 수 있습니다(현재 " + round.getStatus() + ").");
		RoundCourse segment = r.roundCourseId() != null
				? roundCourses.findById(r.roundCourseId()).filter(c -> c.getRoundId().equals(roundId))
						.orElseThrow(() -> NotFoundException.of("코스 사용 구간", r.roundCourseId()))
				: roundCourses.findByRoundIdAndFinishedAtIsNull(roundId).orElseThrow(
						() -> new IllegalArgumentException("사용 중인 코스가 없습니다. 코스 사용 구간(roundCourseId)을 지정하세요."));
		String courseName = lookup.courseNames(Set.of(segment.getCourseId())).getOrDefault(segment.getCourseId(),
				String.valueOf(segment.getCourseId()));
		HoleInfo hole = lookup.holeOfCourse(r.holeId(), segment.getCourseId()).orElseThrow(
				() -> new IllegalArgumentException("홀(" + r.holeId() + ")은 이 구간의 코스(" + courseName + ")에 속하지 않습니다."));

		Map<Long, RoundPlayer> roundPlayers = players.findByRoundIdOrderByPlayerOrder(roundId).stream()
				.collect(Collectors.toMap(RoundPlayer::getId, Function.identity()));
		Set<Long> seen = new HashSet<>();
		for (ScoreEntry e : r.entries()) {
			if (!roundPlayers.containsKey(e.roundPlayerId()))
				throw NotFoundException.of("라운드 참가자", e.roundPlayerId());
			if (!seen.add(e.roundPlayerId()))
				throw new IllegalArgumentException("같은 참가자가 두 번 들어 있습니다.");
		}

		Map<Long, Score> existing = scores
				.findByRoundCourseIdAndHoleIdAndRoundPlayerIdIn(segment.getId(), hole.id(), seen).stream()
				.collect(Collectors.toMap(Score::getRoundPlayerId, Function.identity()));
		LocalDateTime now = LocalDateTime.now(clock);
		Long actor = CurrentUser.idOrNull();
		List<String> summary = new ArrayList<>();
		for (ScoreEntry e : r.entries()) {
			RoundPlayer p = roundPlayers.get(e.roundPlayerId());
			Score s = existing.get(p.getId());
			if (s == null)
				s = new Score(p.getId(), segment.getId(), hole.id(), now);
			s.record(e.strokes(), e.putts(), e.penalties() == null ? 0 : e.penalties(), e.fairwayHit(), actor, now);
			scores.save(s);
			summary.add(p.getName() + " " + e.strokes());
		}
		scores.flush();
		for (Long playerId : seen) {
			int[] t = lookup.playerTotals(playerId);
			roundPlayers.get(playerId).updateTotals(t[0], t[1], t[2], t[3]);
		}
		round.touch(now);
		events.record(roundId, RoundEventType.SCORE_RECORDED, segment.getCourseId(),
				seen.size() == 1 ? seen.iterator().next() : null,
				courseName + " " + hole.holeNo() + "번 홀(파" + hole.par() + "): " + String.join(", ", summary));
		return scorecard(roundId);
	}

	@Transactional(readOnly = true)
	public Scorecard scorecard(long roundId) {
		GolfRound round = rounds.findById(roundId).orElseThrow(() -> NotFoundException.of("라운드", roundId));
		List<RoundCourse> segments = roundCourses.findByRoundIdOrderBySeqNo(roundId);
		Set<Long> courseIds = segments.stream().map(RoundCourse::getCourseId).collect(Collectors.toSet());
		Map<Long, List<HoleInfo>> holes = lookup.holesOfCourses(courseIds);
		Map<Long, String> courseNames = lookup.courseNames(courseIds);
		List<ScorecardSegment> segmentInfos = segments.stream().map(c -> {
			List<HoleInfo> list = holes.getOrDefault(c.getCourseId(), List.of());
			return new ScorecardSegment(c.getId(), c.getSeqNo(), c.getCourseId(), courseNames.get(c.getCourseId()),
					list.stream().mapToInt(HoleInfo::par).sum(),
					list.stream().map(h -> new HoleResponse(h.id(), h.holeNo(), h.par(), h.handicap())).toList());
		}).toList();

		List<RoundPlayer> roundPlayers = players.findByRoundIdOrderByPlayerOrder(roundId);
		Map<Long, Integer> segmentOrder = segments.stream()
				.collect(Collectors.toMap(RoundCourse::getId, c -> (int) c.getSeqNo()));
		Map<Long, Short> holeNo = holes.values().stream().flatMap(List::stream)
				.collect(Collectors.toMap(HoleInfo::id, HoleInfo::holeNo, (a, b) -> a));
		Map<Long, List<Score>> byPlayer = roundPlayers.isEmpty() ? Map.of()
				: scores.findByRoundPlayerIdIn(roundPlayers.stream().map(RoundPlayer::getId).toList()).stream()
						.collect(Collectors.groupingBy(Score::getRoundPlayerId));
		Comparator<Score> order = Comparator.<Score>comparingInt(s -> segmentOrder.getOrDefault(s.getRoundCourseId(), 0))
				.thenComparingInt(s -> holeNo.getOrDefault(s.getHoleId(), (short) 0));
		List<PlayerScorecard> cards = roundPlayers.stream()
				.map(p -> new PlayerScorecard(p.getId(), p.getPlayerOrder(), p.getName(), p.getHolesPlayed(),
						p.getTotalStrokes(), p.getTotalPutts(), p.getParPlayed(), p.getScoreToPar(),
						byPlayer.getOrDefault(p.getId(), List.of()).stream().sorted(order).map(ScoreResponse::of)
								.toList()))
				.toList();
		return new Scorecard(round.getId(), round.getRoundNo(), round.getStatus(), segmentInfos, cards);
	}
}
