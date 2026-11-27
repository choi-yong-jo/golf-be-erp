package com.golf.erp.lodging;

/**
 * 객실 상태(lodging_room.room_status).
 * <ul>
 * <li>AVAILABLE: 판매 가능</li>
 * <li>CLEANING: 퇴실 후 청소 중. 앞으로의 예약은 받을 수 있지만 청소 완료 전에는 입실 처리할 수 없다.</li>
 * <li>MAINTENANCE: 보수 중. 예약·입실 불가</li>
 * <li>CLOSED: 운영 중지. 예약·입실 불가</li>
 * </ul>
 */
public enum LodgingRoomStatus {
	AVAILABLE, CLEANING, MAINTENANCE, CLOSED;

	/** 새 예약을 받을 수 있는 상태 */
	public boolean isBookable() {
		return this == AVAILABLE || this == CLEANING;
	}
}
