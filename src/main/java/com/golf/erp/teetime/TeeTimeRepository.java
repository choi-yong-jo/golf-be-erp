package com.golf.erp.teetime;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public interface TeeTimeRepository extends JpaRepository<TeeTime, Long>, JpaSpecificationExecutor<TeeTime> {

	/** 예약 앱과 같은 방식(SELECT ... FOR UPDATE, 3초 대기)으로 티타임 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select t from TeeTime t where t.id = :id")
	Optional<TeeTime> findByIdForUpdate(Long id);

	/** ux_tee_time_slot 과 같은 조건(코스 null 은 '' 로 비교) */
	@Query("""
			select case when count(t) > 0 then true else false end from TeeTime t
			where t.golfCourseId = :golfCourseId and t.playDate = :playDate and t.startTime = :startTime
			  and coalesce(t.course, '') = :course""")
	boolean existsSlot(Long golfCourseId, LocalDate playDate, LocalTime startTime, String course);
}
