package com.golf.erp.common;

/** 현재 상태와 충돌하는 요청(허용되지 않는 상태 변경, 중복 등). HTTP 409 */
public class ConflictException extends RuntimeException {
	public ConflictException(String message) {
		super(message);
	}
}
