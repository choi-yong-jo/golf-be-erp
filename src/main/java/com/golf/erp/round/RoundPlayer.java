package com.golf.erp.round;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.round_player — 라운드 참가자(회원 또는 비회원). checked_out_at 이 NULL 이면 아직 골프장 안에 있다.
 * total_* 는 스코어를 저장할 때마다 다시 계산한다.
 */
@Entity
@Table(name = "round_player")
public class RoundPlayer {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "round_player_id")
	private Long id;
	@Column(name = "round_id", nullable = false)
	private Long roundId;
	@Column(name = "player_order", nullable = false)
	private Short playerOrder;
	@Column(name = "member_id")
	private Long memberId;
	@Column(name = "player_name", nullable = false, length = 100)
	private String name;
	@Column(length = 30)
	private String phone;
	@Column(name = "handicap")
	private Short handicap;
	@Column(name = "locker_no", length = 10)
	private String lockerNo;
	@Column(name = "checked_in_at", nullable = false)
	private LocalDateTime checkedInAt;
	@Column(name = "checked_out_at")
	private LocalDateTime checkedOutAt;
	@Column(name = "holes_played", nullable = false)
	private Short holesPlayed = 0;
	@Column(name = "total_strokes", nullable = false)
	private Short totalStrokes = 0;
	@Column(name = "total_putts", nullable = false)
	private Short totalPutts = 0;
	@Column(name = "par_played", nullable = false)
	private Short parPlayed = 0;

	protected RoundPlayer() {
	}

	public RoundPlayer(Long roundId, short playerOrder, Long memberId, String name, String phone, Short handicap,
			String lockerNo, LocalDateTime now) {
		this.roundId = roundId;
		this.playerOrder = playerOrder;
		this.memberId = memberId;
		this.name = name;
		this.phone = phone;
		this.handicap = handicap;
		this.lockerNo = lockerNo;
		this.checkedInAt = now;
	}

	void update(String name, String phone, Short handicap, String lockerNo) {
		this.name = name;
		this.phone = phone;
		this.handicap = handicap;
		this.lockerNo = lockerNo;
	}

	void checkOut(LocalDateTime now) {
		if (checkedOutAt != null)
			throw new ConflictException(name + " 님은 이미 체크아웃했습니다.");
		this.checkedOutAt = now;
	}

	/** 라운드 종료·취소 때 남아 있는 참가자만 체크아웃 */
	void checkOutIfPresent(LocalDateTime now) {
		if (checkedOutAt == null)
			this.checkedOutAt = now;
	}

	void updateTotals(int holesPlayed, int totalStrokes, int totalPutts, int parPlayed) {
		this.holesPlayed = (short) holesPlayed;
		this.totalStrokes = (short) totalStrokes;
		this.totalPutts = (short) totalPutts;
		this.parPlayed = (short) parPlayed;
	}

	public boolean isCheckedOut() {
		return checkedOutAt != null;
	}

	public Long getId() {
		return id;
	}

	public Long getRoundId() {
		return roundId;
	}

	public Short getPlayerOrder() {
		return playerOrder;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

	public Short getHandicap() {
		return handicap;
	}

	public String getLockerNo() {
		return lockerNo;
	}

	public LocalDateTime getCheckedInAt() {
		return checkedInAt;
	}

	public LocalDateTime getCheckedOutAt() {
		return checkedOutAt;
	}

	public Short getHolesPlayed() {
		return holesPlayed;
	}

	public Short getTotalStrokes() {
		return totalStrokes;
	}

	public Short getTotalPutts() {
		return totalPutts;
	}

	public Short getParPlayed() {
		return parPlayed;
	}

	/** 타수 - 친 홀의 파 합계(오버/언더). 친 홀이 없으면 null */
	public Integer getScoreToPar() {
		return holesPlayed == 0 ? null : totalStrokes - parPlayed;
	}
}
