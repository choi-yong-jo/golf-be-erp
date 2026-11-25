package com.golf.erp.teetime.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 예약 현황 한 줄. reservation* / member* 는 활성 예약이 없으면 null */
public record TeeTimeBoardRow(Long teeTimeId, Long golfCourseId, String courseName, String course,
		LocalDate playDate, LocalTime startTime, String holeType, Short capacity, BigDecimal greenFee,
		String teeTimeStatus, Long reservationId, String reservationNo, String reservationStatus,
		Integer playerCount, Long memberId, String memberName, String memberPhone) {
}
