package com.golf.erp.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** 목록 응답 공통 형식: {items, page, size, total} (page 는 0부터) */
public record PageResult<T>(List<T> items, int page, int size, long total) {

	public static <E, T> PageResult<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
		return new PageResult<>(page.getContent().stream().<T>map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements());
	}
}
