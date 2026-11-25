package com.golf.erp.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationPlayerRepository extends JpaRepository<ReservationPlayer, Long> {
	List<ReservationPlayer> findByReservationIdOrderByPlayerOrder(Long reservationId);
}
