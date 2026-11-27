package com.golf.erp.teetime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** 티타임 API 요청/응답 */
public final class TeeTimeDtos {
	private static final String HOLE_TYPE = "9H|18H|27H|36H";
	private static final String HOLE_TYPE_MESSAGE = "9H, 18H, 27H, 36H 중 하나여야 합니다.";

	private TeeTimeDtos() {
	}

	/**
	 * 티타임 1건 등록. course 는 골프장에 등록된 활성 코스 코드(예: OUT, IN), 생략 가능.
	 * greenFee(1인) 생략 시 요금 정책으로 채우고, 정책도 없으면 비워 둔다(예약 앱에 노출되지 않음).
	 */
	public record TeeTimeCreateRequest(@NotNull Long golfCourseId, @Size(max = 20) String course,
			@NotNull LocalDate playDate, @NotNull LocalTime startTime,
			@Pattern(regexp = HOLE_TYPE, message = HOLE_TYPE_MESSAGE) String holeType,
			@Min(1) @Max(8) Short capacity,
			@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal greenFee) {
	}

	/**
	 * 기간 일괄 생성. fromDate~toDate(최대 62일) 각 날짜에 firstTime~lastTime 을 intervalMinutes 간격으로 만든다.
	 * daysOfWeek(1=월~7=일)를 주면 해당 요일만. greenFee 생략 시 슬롯마다 요금 정책으로 채운다.
	 * 이미 있는 슬롯과 휴장 구간, 지난 시각은 건너뛴다.
	 */
	public record TeeTimeGenerateRequest(@NotNull Long golfCourseId, @Size(max = 20) String course,
			@NotNull LocalDate fromDate, @NotNull LocalDate toDate, @NotNull LocalTime firstTime,
			@NotNull LocalTime lastTime, @NotNull @Min(5) @Max(120) Integer intervalMinutes,
			Set<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
			@Pattern(regexp = HOLE_TYPE, message = HOLE_TYPE_MESSAGE) String holeType,
			@Min(1) @Max(8) Short capacity,
			@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal greenFee) {
	}

	/**
	 * requested: 계산된 슬롯 수, created: 새로 만든 수, skippedExisting: 이미 있던 슬롯, skippedClosure: 휴장 구간,
	 * skippedPast: 이미 지난 시각, withoutGreenFee: 요금을 정하지 못해 그린피 없이 만든 수(예약 앱에는 노출되지 않음)
	 */
	public record TeeTimeGenerateResult(int requested, int created, int skippedExisting, int skippedClosure,
			int skippedPast, int withoutGreenFee) {
	}

	/** 슬롯(골프장·코스·날짜·시각)은 바꿀 수 없다. greenFee 를 null 로 보내면 그린피를 비운다. */
	public record TeeTimeUpdateRequest(
			@NotNull @Pattern(regexp = HOLE_TYPE, message = HOLE_TYPE_MESSAGE) String holeType,
			@NotNull @Min(1) @Max(8) Short capacity,
			@DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal greenFee) {
	}

	/** OPEN, BLOCKED, CLOSED (BOOKED 는 예약 모듈만 지정) */
	public record TeeTimeStatusRequest(@NotNull TeeTimeStatus status) {
	}

	/** 티타임을 점유한 활성 예약(PENDING, PENDING_PAYMENT, PAYING, CONFIRMED) 요약 */
	public record ReservationSummary(Long reservationId, String reservationNo, String status, Integer playerCount,
			Long memberId, String memberName, String memberPhone) {
	}

	/** reservation: 활성 예약(없으면 null) */
	public record TeeTimeResponse(Long id, Long golfCourseId, String golfCourseName, String course,
			LocalDate playDate, LocalTime startTime, String holeType, Short capacity, BigDecimal greenFee,
			TeeTimeStatus status, Long version, ReservationSummary reservation) {
	}

	/** 예약 현황: 기간 내 티타임 목록과 상태별 집계 */
	public record TeeTimeBoard(Long golfCourseId, LocalDate from, LocalDate to, int total, int open, int booked,
			int blocked, int closed, List<TeeTimeResponse> items) {
	}
}
