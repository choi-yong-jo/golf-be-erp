package com.golf.erp.equipment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 렌탈 모듈 테이블(equipment_rental_item)에서 장비 사용 여부를 읽는다. 렌탈 엔티티를 참조하지 않으려고 SQL 로 읽는다. */
@Component
class EquipmentRentalLookup {
	private final JdbcTemplate jdbc;

	EquipmentRentalLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 아직 반납되지 않은(예약·대여·부분 반납) 렌탈 항목에 묶여 있는지 */
	boolean isRentedOut(long equipmentId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM golf.equipment_rental_item"
				+ " WHERE equipment_id = ? AND item_status IN ('RESERVED','RENTED','PARTIALLY_RETURNED'))",
				Boolean.class, equipmentId));
	}

	/** 렌탈 이력에 한 번이라도 쓰였는지(물리 삭제 가능 여부) */
	boolean isReferenced(long equipmentId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM golf.equipment_rental_item WHERE equipment_id = ?)", Boolean.class,
				equipmentId));
	}
}
