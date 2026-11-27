package com.golf.erp.common;

/** 대상 리소스가 없음. HTTP 404 */
public class NotFoundException extends RuntimeException {
	public NotFoundException(String message) {
		super(message);
	}

	public static NotFoundException of(String what, Object id) {
		return new NotFoundException(what + "을(를) 찾을 수 없습니다: " + id);
	}
}
