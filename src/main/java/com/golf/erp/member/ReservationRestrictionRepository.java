package com.golf.erp.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRestrictionRepository extends JpaRepository<ReservationRestriction, Long> {
	List<ReservationRestriction> findByMemberIdOrderByStartsAtDesc(Long memberId);

	@Query("""
			select r from ReservationRestriction r
			where r.memberId = :memberId and r.startsAt <= :at and (r.endsAt is null or r.endsAt > :at)
			order by r.startsAt""")
	List<ReservationRestriction> findActive(Long memberId, LocalDateTime at);
}
