package com.golf.erp.common;

/** 이미 예약된 티타임, 허용되지 않는 상태 변경 등 현재 상태와 충돌하는 요청. HTTP 409로 응답한다. */
public class ConflictException extends RuntimeException {
	public ConflictException(String message) {
		super(message);
	}
}
