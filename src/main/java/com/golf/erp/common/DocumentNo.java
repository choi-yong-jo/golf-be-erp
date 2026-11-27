package com.golf.erp.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 업무 번호(예: RD-20261126-000123). id 를 포함하므로 저장(flush) 뒤에 만든다.
 * NOT NULL 번호 컬럼은 먼저 {@link #temporary()} 로 넣고, saveAndFlush 후 of(...) 로 바꾼다.
 */
public final class DocumentNo {
	private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

	private DocumentNo() {
	}

	public static String of(String prefix, LocalDate date, long id) {
		return prefix + "-" + date.format(DAY) + "-" + String.format("%06d", id);
	}

	/** 저장 전 임시 번호(36자). 같은 트랜잭션 안에서 of(...) 로 바꾼다. */
	public static String temporary() {
		return UUID.randomUUID().toString();
	}
}
