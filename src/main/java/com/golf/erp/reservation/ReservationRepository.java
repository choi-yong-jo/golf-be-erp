package com.golf.erp.reservation;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
	boolean existsByTeeTimeIdAndStatusIn(Long teeTimeId, Collection<ReservationStatus> statuses);

	/** 예약 처리(결제·취소·완료 등)를 예약 앱과 직렬화하려고 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select r from Reservation r where r.id = :id")
	Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
