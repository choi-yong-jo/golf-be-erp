package com.golf.erp.lodging;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 한 행의 이용 시간. DDL ck_lodging_booking_mode 를 그대로 옮긴 규칙이다.
 * <ul>
 * <li>DAY_USE: 입실 시각 + dayUseHours 시간 = 퇴실 시각, stay_nights = 0</li>
 * <li>OVERNIGHT: 날짜 16:00 입실 → 다음 날 11:00 퇴실, stay_nights = 1, day_use_hours = 0</li>
 * </ul>
 * 기간은 반열린 구간 [checkIn, checkOut). 연박의 n번째 밤(D+1 16:00~)은 앞 밤(~D+1 11:00)과 겹치지 않는다.
 */
public record LodgingSlot(LodgingBookingType type, LocalDateTime checkIn, LocalDateTime checkOut, short stayNights,
		short dayUseHours) {

	public static final LocalTime OVERNIGHT_CHECK_IN = LocalTime.of(16, 0);
	public static final LocalTime OVERNIGHT_CHECK_OUT = LocalTime.of(11, 0);
	/** 대실 시간 상한(요청 검증용) */
	public static final int MAX_DAY_USE_HOURS = 24;

	public LodgingSlot {
		if (type == LodgingBookingType.DAY_USE) {
			if (dayUseHours <= 0 || stayNights != 0 || !checkOut.equals(checkIn.plusHours(dayUseHours)))
				throw new IllegalArgumentException("대실 예약은 입실 시각에서 대실 시간(1시간 이상) 뒤가 퇴실 시각이어야 합니다.");
		} else {
			if (stayNights != 1 || dayUseHours != 0 || !checkIn.toLocalTime().equals(OVERNIGHT_CHECK_IN)
					|| !checkOut.toLocalTime().equals(OVERNIGHT_CHECK_OUT)
					|| !checkOut.toLocalDate().equals(checkIn.toLocalDate().plusDays(1)))
				throw new IllegalArgumentException("1박 예약은 16:00 입실, 다음 날 11:00 퇴실이어야 합니다.");
		}
	}

	/** date 16:00 → date+1 11:00 */
	public static LodgingSlot overnight(LocalDate date) {
		return new LodgingSlot(LodgingBookingType.OVERNIGHT, date.atTime(OVERNIGHT_CHECK_IN),
				date.plusDays(1).atTime(OVERNIGHT_CHECK_OUT), (short) 1, (short) 0);
	}

	/** date checkInTime → + hours 시간. 입실 시각은 분 단위(초 없음)만 받는다. */
	public static LodgingSlot dayUse(LocalDate date, LocalTime checkInTime, int hours) {
		if (checkInTime == null)
			throw new IllegalArgumentException("대실 예약은 입실 시각(checkInTime)이 필요합니다.");
		if (checkInTime.getSecond() != 0 || checkInTime.getNano() != 0)
			throw new IllegalArgumentException("입실 시각은 분 단위로 입력하세요(예: 13:30).");
		if (hours < 1 || hours > MAX_DAY_USE_HOURS)
			throw new IllegalArgumentException("대실 시간은 1~" + MAX_DAY_USE_HOURS + "시간이어야 합니다.");
		LocalDateTime in = date.atTime(checkInTime);
		return new LodgingSlot(LodgingBookingType.DAY_USE, in, in.plusHours(hours), (short) 0, (short) hours);
	}

	public boolean overlaps(LocalDateTime otherIn, LocalDateTime otherOut) {
		return otherIn.isBefore(checkOut) && otherOut.isAfter(checkIn);
	}
}
