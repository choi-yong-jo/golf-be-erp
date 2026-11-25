package com.golf.erp.common;

import java.util.List;

/** 목록 응답 공통 형식: {items, page, size, total} */
public record PageResult<T>(List<T> items, int page, int size, long total) {

	public static void validate(int page, int size) {
		if (page < 0 || size < 1 || size > 100)
			throw new IllegalArgumentException("page는 0 이상, size는 1~100이어야 합니다.");
	}
}
