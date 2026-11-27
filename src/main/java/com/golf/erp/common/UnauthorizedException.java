package com.golf.erp.common;

/** 로그인 실패 등 인증 오류. HTTP 401 */
public class UnauthorizedException extends RuntimeException {
	public UnauthorizedException(String message) {
		super(message);
	}
}
