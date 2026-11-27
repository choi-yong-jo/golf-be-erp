package com.golf.erp.common;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

/**
 * 목록 검색 조건 헬퍼. 값이 null(문자열은 공백 포함)이면 null 을 돌려주므로
 * {@code Specification.allOf(Specs.eq("a", a), Specs.like(kw, "name"))} 처럼 선택 필터를 그대로 이어 붙인다.
 * 속성 이름은 엔티티 필드명이다.
 */
public final class Specs {
	private Specs() {
	}

	public static <T> Specification<T> eq(String attr, Object value) {
		if (isEmpty(value))
			return null;
		return (root, q, cb) -> cb.equal(path(root, attr), value);
	}

	public static <T> Specification<T> in(String attr, Collection<?> values) {
		if (values == null || values.isEmpty())
			return null;
		return (root, q, cb) -> path(root, attr).in(values);
	}

	/** 대소문자 무시 부분 일치. 여러 속성을 주면 그중 하나라도 맞으면 된다(OR). */
	public static <T> Specification<T> like(String keyword, String... attrs) {
		if (keyword == null || keyword.isBlank())
			return null;
		String pattern = "%" + escape(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
		return (root, q, cb) -> cb.or(Arrays.stream(attrs)
				.map(a -> cb.like(cb.lower(root.<String>get(a)), pattern, '\\'))
				.toArray(Predicate[]::new));
	}

	public static <T, Y extends Comparable<? super Y>> Specification<T> gte(String attr, Y value) {
		if (value == null)
			return null;
		return (root, q, cb) -> cb.greaterThanOrEqualTo(root.<Y>get(attr), value);
	}

	public static <T, Y extends Comparable<? super Y>> Specification<T> lte(String attr, Y value) {
		if (value == null)
			return null;
		return (root, q, cb) -> cb.lessThanOrEqualTo(root.<Y>get(attr), value);
	}

	/** 반열린 구간의 끝(미포함). 날짜 to 를 다음 날 0시로 바꿔 넣을 때 쓴다. */
	public static <T, Y extends Comparable<? super Y>> Specification<T> lt(String attr, Y value) {
		if (value == null)
			return null;
		return (root, q, cb) -> cb.lessThan(root.<Y>get(attr), value);
	}

	public static <T> Specification<T> isNull(String attr) {
		return (root, q, cb) -> cb.isNull(path(root, attr));
	}

	public static <T> Specification<T> isNotNull(String attr) {
		return (root, q, cb) -> cb.isNotNull(path(root, attr));
	}

	private static Path<Object> path(Root<?> root, String attr) {
		Path<Object> p = null;
		for (String part : attr.split("\\."))
			p = p == null ? root.get(part) : p.get(part);
		return p;
	}

	private static boolean isEmpty(Object value) {
		return value == null || value instanceof String s && s.isBlank();
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
