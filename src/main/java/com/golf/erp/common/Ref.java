package com.golf.erp.common;

/**
 * 다른 모듈 테이블 참조 검증용 목록(RefChecker). 모듈끼리는 엔티티를 직접 참조하지 않고 id 로만 연결한다.
 * 테이블·컬럼 이름이 SQL 에 그대로 들어가므로 여기 정의된 값만 쓴다.
 */
public enum Ref {
	MEMBER("golf.member", "member_id", "회원"),
	ADMIN_USER("golf.admin_user", "admin_user_id", "직원 계정"),
	GOLF_COURSE("golf.golf_course", "golf_course_id", "골프장"),
	COURSE("golf.course", "course_id", "코스"),
	HOLE("golf.hole", "hole_id", "홀"),
	TEE_TIME("golf.tee_time", "tee_time_id", "티타임"),
	RESERVATION("golf.reservation", "reservation_id", "예약"),
	ROUND("golf.round", "round_id", "라운드"),
	ROUND_PLAYER("golf.round_player", "round_player_id", "라운드 참가자"),
	CADDIE("golf.caddie", "caddie_id", "캐디"),
	CART("golf.cart", "cart_id", "카트"),
	STAFF("golf.staff", "staff_id", "직원"),
	EQUIPMENT("golf.equipment", "equipment_id", "장비"),
	FACILITY("golf.facility", "facility_id", "부대시설"),
	FACILITY_ROOM_TYPE("golf.facility_room_type", "facility_room_type_id", "공간 종류"),
	FACILITY_UNIT("golf.facility_unit", "facility_unit_id", "회의실·연회장 공간"),
	LODGING_ROOM_TYPE("golf.lodging_room_type", "lodging_room_type_id", "객실 타입"),
	LODGING_ROOM("golf.lodging_room", "lodging_room_id", "객실"),
	STORE_BRAND("golf.store_brand", "store_brand_id", "입점 브랜드"),
	TENANT_STORE("golf.tenant_store", "store_id", "입점 매장"),
	STORE_MENU_ITEM("golf.store_menu_item", "store_menu_item_id", "메뉴"),
	BOARD("golf.board", "board_id", "게시글"),
	COMMON_FILE("golf.common_file", "common_file_id", "파일"),
	INVENTORY_ITEM("golf.inventory_item", "inventory_item_id", "재고 품목"),
	PROMOTION("golf.promotion", "promotion_id", "프로모션"),
	ACCOUNT("golf.account", "account_id", "계정과목"),
	RENTAL_EQUIPMENT("golf.rental_equipment", "rental_equipment_id", "렌탈 장비");

	final String table;
	final String idColumn;
	public final String label;

	Ref(String table, String idColumn, String label) {
		this.table = table;
		this.idColumn = idColumn;
		this.label = label;
	}
}
