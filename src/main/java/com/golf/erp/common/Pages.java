package com.golf.erp.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** page(0부터)·size(1~100) 검증 후 Pageable 생성 */
public final class Pages {
	public static final int MAX_SIZE = 100;

	private Pages() {
	}

	public static Pageable of(int page, int size, Sort sort) {
		if (page < 0 || size < 1 || size > MAX_SIZE)
			throw new IllegalArgumentException("page는 0 이상, size는 1~" + MAX_SIZE + "이어야 합니다.");
		return PageRequest.of(page, size, sort);
	}
}
