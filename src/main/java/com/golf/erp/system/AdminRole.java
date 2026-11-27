package com.golf.erp.system;

/**
 * admin_user.role (직무 구분). ADMIN 만 특별 취급(모든 API 허용)하고, 나머지는 표시용이다.
 * 실제 API 접근 권한은 system_role 에 묶인 permission 으로 정한다.
 */
public enum AdminRole {
	ADMIN, MANAGER, FRONT, STARTER, CADDIE_MASTER, STAFF;

	public static AdminRole parse(String value) {
		try {
			return valueOf(value);
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new IllegalArgumentException("role 은 ADMIN, MANAGER, FRONT, STARTER, CADDIE_MASTER, STAFF 중 하나여야 합니다.");
		}
	}
}
