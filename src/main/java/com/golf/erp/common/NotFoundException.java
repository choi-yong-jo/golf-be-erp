package com.golf.erp.common;

/** 대상 리소스가 없음. HTTP 404로 응답한다. */
public class NotFoundException extends RuntimeException {
	public NotFoundException(String message) {
		super(message);
	}
}
