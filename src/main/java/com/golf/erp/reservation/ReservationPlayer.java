package com.golf.erp.reservation;

import jakarta.persistence.*;

/** golf.reservation_player. playerOrder 1 = 예약자 본인(isBookingMember=true), 2부터 동반자 */
@Entity
@Table(name = "reservation_player")
public class ReservationPlayer {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reservation_player_id")
	private Long reservationPlayerId;
	@Column(name = "reservation_id", nullable = false)
	private Long reservationId;
	@Column(name = "player_order", nullable = false)
	private Short playerOrder;
	@Column(name = "player_name", nullable = false, length = 100)
	private String playerName;
	@Column(length = 30)
	private String phone;
	@Column(name = "member_id")
	private Long memberId;
	@Column(name = "is_booking_member", nullable = false)
	private boolean bookingMember;

	protected ReservationPlayer() {
	}

	public ReservationPlayer(Long reservationId, short playerOrder, String playerName, String phone, Long memberId,
			boolean bookingMember) {
		this.reservationId = reservationId;
		this.playerOrder = playerOrder;
		this.playerName = playerName;
		this.phone = phone;
		this.memberId = memberId;
		this.bookingMember = bookingMember;
	}

	public Long getReservationPlayerId() {
		return reservationPlayerId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public Short getPlayerOrder() {
		return playerOrder;
	}

	public String getPlayerName() {
		return playerName;
	}

	public String getPhone() {
		return phone;
	}

	public Long getMemberId() {
		return memberId;
	}

	public boolean isBookingMember() {
		return bookingMember;
	}
}
