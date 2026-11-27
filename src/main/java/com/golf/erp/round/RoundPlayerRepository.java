package com.golf.erp.round;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoundPlayerRepository extends JpaRepository<RoundPlayer, Long> {
	List<RoundPlayer> findByRoundIdOrderByPlayerOrder(Long roundId);

	List<RoundPlayer> findByRoundIdInOrderByRoundIdAscPlayerOrderAsc(Collection<Long> roundIds);

	/** 지금 골프장 안에 있는(체크아웃 전) 회원 참가자. ux_round_player_active_member 와 같은 조건 */
	Optional<RoundPlayer> findFirstByMemberIdAndCheckedOutAtIsNull(Long memberId);

	/** 같은 골프장에서 지금 쓰고 있는(체크아웃 전) 락커 수 */
	@Query("""
			select count(p) from RoundPlayer p, GolfRound r
			where r.id = p.roundId and r.golfCourseId = :golfCourseId and p.lockerNo = :lockerNo
			  and p.checkedOutAt is null and p.id <> :exceptPlayerId""")
	long countLockerInUse(Long golfCourseId, String lockerNo, Long exceptPlayerId);
}
