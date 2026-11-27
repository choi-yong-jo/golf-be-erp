package com.golf.erp.security;

/**
 * 권한 단위(모듈)와 URL 매핑. 모듈마다 {CODE}_READ, {CODE}_WRITE 권한이 있다(golf.permission 에 자동 등록).
 * <ul>
 * <li>GET 요청: READ 또는 WRITE</li>
 * <li>그 밖의 요청: WRITE</li>
 * <li>admin_user.role = ADMIN 은 모든 API 허용</li>
 * </ul>
 * 새 API 경로를 만들면 반드시 여기에 추가한다. 등록되지 않은 /api/** 경로는 로그인만 되어 있으면 허용된다
 * (/api/dashboard, /api/files: 모든 직원이 쓰는 공통 기능).
 */
public enum ErpModule {
	MEMBER("회원", "/api/members/**"),
	COURSE("골프장·코스", "/api/golf-courses/**", "/api/courses/**", "/api/holes/**"),
	FEE("요금", "/api/fee-policies/**", "/api/service-fees/**"),
	TEE_TIME("티타임", "/api/tee-times/**"),
	RESERVATION("예약", "/api/reservations/**"),
	PAYMENT("예약 결제", "/api/payments/**"),
	SETTLEMENT("정산", "/api/settlements/**", "/api/daily-settlements/**"),
	ROUND("라운드", "/api/rounds/**"),
	RESOURCE("캐디·카트", "/api/caddies/**", "/api/carts/**"),
	STAFF("인사", "/api/staff/**", "/api/staff-shifts/**"),
	PAYROLL("급여", "/api/payrolls/**"),
	EQUIPMENT("장비", "/api/equipment/**"),
	FACILITY("회의실·연회장", "/api/facilities/**", "/api/facility-room-types/**", "/api/facility-units/**",
			"/api/facility-bookings/**"),
	LODGING("숙박", "/api/lodging-room-types/**", "/api/lodging-rooms/**", "/api/lodging-bookings/**"),
	TENANT("입점 매장·메뉴", "/api/store-brands/**", "/api/tenant-stores/**"),
	STORE_ORDER("매장 주문", "/api/store-orders/**"),
	INVENTORY("재고·구매", "/api/inventory-items/**", "/api/inventory-transactions/**", "/api/purchase-orders/**"),
	CRM("고객관리", "/api/notifications/**", "/api/promotions/**"),
	BOARD("게시판", "/api/boards/**"),
	ACCOUNTING("회계", "/api/accounts/**", "/api/journal-entries/**", "/api/operating-expenses/**"),
	RENTAL("장비 렌탈", "/api/rental-equipment/**", "/api/rentals/**"),
	SYSTEM("시스템 관리", "/api/admin-users/**", "/api/roles/**", "/api/permissions/**"),
	AUDIT("감사 로그", "/api/audit-logs/**");

	public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

	public final String label;
	final String[] paths;

	ErpModule(String label, String... paths) {
		this.label = label;
		this.paths = paths;
	}

	public String readAuthority() {
		return name() + "_READ";
	}

	public String writeAuthority() {
		return name() + "_WRITE";
	}
}
