package com.golf.erp.reservation;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Collection;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
	boolean existsByTeeTimeId(Long teeTimeId);

	boolean existsByTeeTimeIdAndReservationStatusIn(Long teeTimeId, Collection<ReservationStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select r from Reservation r where r.reservationId = :id")
	Optional<Reservation> findByIdForUpdate(Long id);
}
