package com.golf.erp.lodging;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.lodging_room. 객실 한 칸. 예약 등록·입실·퇴실·상태 변경 전에 이 행을 PESSIMISTIC_WRITE 로 잠가
 * 같은 객실의 처리를 직렬화한다(잠금 순서: 객실 → 예약).
 */
@Entity
@Table(name = "lodging_room")
public class LodgingRoom {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "lodging_room_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lodging_room_type_id", nullable = false)
	private LodgingRoomType roomType;
	@Column(name = "room_code", nullable = false, length = 40)
	private String roomCode;
	@Column(name = "room_name", length = 100)
	private String roomName;
	@Enumerated(EnumType.STRING)
	@Column(name = "room_status", nullable = false, length = 20)
	private LodgingRoomStatus status = LodgingRoomStatus.AVAILABLE;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;

	protected LodgingRoom() {
	}

	public LodgingRoom(LodgingRoomType roomType, String roomCode, String roomName, Long actorId, LocalDateTime now) {
		this.roomType = roomType;
		this.roomCode = roomCode;
		this.roomName = roomName;
		this.createdBy = actorId;
		this.updatedBy = actorId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 객실 번호·이름·타입. 타입 변경 가능 여부(같은 골프장, 진행 예약 없음)는 서비스가 확인한다. */
	public void update(LodgingRoomType roomType, String roomCode, String roomName, Long actorId, LocalDateTime now) {
		this.roomType = roomType;
		this.roomCode = roomCode;
		this.roomName = roomName;
		touch(actorId, now);
	}

	public void changeStatus(LodgingRoomStatus status, Long actorId, LocalDateTime now) {
		this.status = status;
		touch(actorId, now);
	}

	/** 퇴실 처리 시 판매 가능 객실을 청소 중으로 바꾼다(보수·운영 중지 상태는 그대로 둔다). */
	void startCleaning(Long actorId, LocalDateTime now) {
		if (status == LodgingRoomStatus.AVAILABLE)
			changeStatus(LodgingRoomStatus.CLEANING, actorId, now);
	}

	/** CLEANING → AVAILABLE */
	public void markCleaned(Long actorId, LocalDateTime now) {
		if (status != LodgingRoomStatus.CLEANING)
			throw new ConflictException("청소 중(CLEANING)인 객실만 청소 완료 처리할 수 있습니다. 현재 상태: " + status);
		changeStatus(LodgingRoomStatus.AVAILABLE, actorId, now);
	}

	/** 새 예약·예약 기간 변경 가능 여부: 객실이 보수·운영 중지가 아니고 타입이 활성이어야 한다. */
	void requireBookable() {
		if (!status.isBookable())
			throw new ConflictException("예약을 받을 수 없는 객실입니다(" + status + "): " + label());
		if (!roomType.isActive())
			throw new ConflictException("비활성 객실 타입의 객실은 예약할 수 없습니다: " + roomType.getName());
	}

	String label() {
		return roomName == null ? roomCode : roomCode + " " + roomName;
	}

	private void touch(Long actorId, LocalDateTime now) {
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public LodgingRoomType getRoomType() {
		return roomType;
	}

	public String getRoomCode() {
		return roomCode;
	}

	public String getRoomName() {
		return roomName;
	}

	public LodgingRoomStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}
}
