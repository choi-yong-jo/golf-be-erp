package com.golf.erp.round;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GolfRoundRepository extends JpaRepository<GolfRound, Long>, JpaSpecificationExecutor<GolfRound> {

	/** 라운드를 바꾸는 모든 처리(진행·참가자·배정·스코어·요금·수납)는 이 잠금으로 직렬화한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from GolfRound r where r.id = :id")
	Optional<GolfRound> findByIdForUpdate(Long id);

	/** 일 마감 계산·마감 때 그날 라운드를 모두 잠가, 진행 중인 요금·수납 변경과 겹치지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from GolfRound r where r.golfCourseId = :golfCourseId and r.playDate = :playDate order by r.id")
	List<GolfRound> lockAllOn(Long golfCourseId, LocalDate playDate);

	Optional<GolfRound> findFirstByReservationIdAndStatusNot(Long reservationId, RoundStatus status);

	long countByGolfCourseIdAndPlayDateAndStatusIn(Long golfCourseId, LocalDate playDate,
			Collection<RoundStatus> statuses);
}
