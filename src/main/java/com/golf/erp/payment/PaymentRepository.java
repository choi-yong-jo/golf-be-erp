package com.golf.erp.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	List<Payment> findByReservationIdOrderByRequestedAtAscPaymentIdAsc(Long reservationId);

	List<Payment> findByReservationIdAndPaymentTypeAndPaymentStatus(Long reservationId, PaymentType type,
			PaymentStatus status);
}
