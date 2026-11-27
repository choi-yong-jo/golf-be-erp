package com.golf.erp.rental;

/**
 * 렌탈 품목 상태 (equipment_rental_item.item_status CHECK).
 * 모두 처리(반납·파손·분실)되면 분실이 하나라도 있으면 LOST, 파손이 있으면 DAMAGED, 아니면 RETURNED.
 */
public enum RentalItemStatus {
	RESERVED, RENTED, PARTIALLY_RETURNED, RETURNED, LOST, DAMAGED, CANCELLED
}
