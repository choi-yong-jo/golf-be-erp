package com.golf.erp.reservation.dto;

import com.golf.erp.payment.Payment;
import com.golf.erp.reservation.Reservation;
import com.golf.erp.reservation.ReservationHistory;
import com.golf.erp.reservation.ReservationPlayer;
import com.golf.erp.teetime.TeeTime;

import java.util.List;

/** 예약 상세: 예약 + 회원/코스 이름 + 티타임 + 플레이어 + 결제 + 변경 이력 */
public record ReservationDetail(Reservation reservation, String memberName, String memberPhone, String courseName,
		TeeTime teeTime, List<ReservationPlayer> players, List<Payment> payments, List<ReservationHistory> histories) {
}
