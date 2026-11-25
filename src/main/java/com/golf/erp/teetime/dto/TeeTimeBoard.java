package com.golf.erp.teetime.dto;

import java.time.LocalDate;
import java.util.List;

/** 예약 현황: 기간 내 티타임 목록과 상태별 집계 */
public record TeeTimeBoard(Long golfCourseId, LocalDate from, LocalDate to, int total, int open, int booked,
		int blocked, int closed, List<TeeTimeBoardRow> items) {
}
