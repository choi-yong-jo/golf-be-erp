package com.golf.erp.teetime;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

public interface TeeTimeRepository extends JpaRepository<TeeTime, Long> {

	/** 예약 앱과 같은 방식(SELECT ... FOR UPDATE, 3초 대기)으로 티타임 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select t from TeeTime t where t.teeTimeId = :id")
	Optional<TeeTime> findByIdForUpdate(Long id);
}
