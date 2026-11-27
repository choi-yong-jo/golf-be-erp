package com.golf.erp.facility;

import com.golf.erp.common.ConflictException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 시설 예약 시간·금액 규칙(시간 단위 요금).
 * <ul>
 * <li>시작·종료 시각은 15분 단위(분이 0/15/30/45, 초·나노초 0). 이용 시간(booking_hours) = 분 / 60 이 소수 둘째 자리에서
 * 정확히 떨어진다(예: 1시간 15분 → 1.25). DDL CHECK booking_hours = EPOCH(end − start)/3600 과 같다.</li>
 * <li>시간당 요금(hourly_rate) = 예약 시점 공간 종류의 시간당 기본요금 스냅샷. 원 단위 정수만 허용.</li>
 * <li>기본 금액 = 시간당 요금 × 이용 시간(정확한 값, 반올림 없음). 할인 ≤ 기본 금액, 합계 = 기본 금액 − 할인.</li>
 * </ul>
 */
final class FacilityPricing {
	/** 예약·가용 조회 한 건의 최대 기간(일). booking_hours NUMERIC(6,2) 범위(9999.99) 안이다. */
	static final int MAX_BOOKING_DAYS = 90;
	/** 시각 단위(분) */
	static final int SLOT_MINUTES = 15;
	/** NUMERIC(14,2) 최대값 */
	static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
	/** NUMERIC(12,2) 에 들어가는 최대 원 단위 요금 */
	static final BigDecimal MAX_HOURLY_RATE = new BigDecimal("9999999999");

	private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

	private FacilityPricing() {
	}

	/** 15분 단위 시각인지 확인. 아니면 400 */
	static void requireSlotBoundary(LocalDateTime t, String label) {
		if (t.getSecond() != 0 || t.getNano() != 0 || t.getMinute() % SLOT_MINUTES != 0)
			throw new IllegalArgumentException(label + "(" + t + ")은(는) 15분 단위(정각·15분·30분·45분, 초 없음)여야 합니다.");
	}

	/**
	 * 기간 검증 후 이용 시간(시간, 소수 둘째 자리, 정확한 값). 15분 단위, end &gt; start, 최대 {@value #MAX_BOOKING_DAYS}일.
	 * 위반 시 400
	 */
	static BigDecimal bookingHours(LocalDateTime startAt, LocalDateTime endAt) {
		requireSlotBoundary(startAt, "시작 시각");
		requireSlotBoundary(endAt, "종료 시각");
		if (!endAt.isAfter(startAt))
			throw new IllegalArgumentException("종료 시각은 시작 시각보다 뒤여야 합니다.");
		long minutes = Duration.between(startAt, endAt).toMinutes();
		if (minutes > (long) MAX_BOOKING_DAYS * 24 * 60)
			throw new IllegalArgumentException("예약 기간은 최대 " + MAX_BOOKING_DAYS + "일입니다.");
		// 15분의 배수 / 60 은 .00/.25/.50/.75 로 정확히 떨어진다.
		return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.UNNECESSARY);
	}

	/** 시간당 기본요금 검증·정리: 0 이상, 원 단위 정수(소수 불가), NUMERIC(12,2) 범위. scale 2 로 돌려준다. 위반 시 400 */
	static BigDecimal hourlyRate(BigDecimal rate) {
		if (rate == null)
			throw new IllegalArgumentException("시간당 기본요금이 필요합니다.");
		if (rate.signum() < 0)
			throw new IllegalArgumentException("시간당 기본요금은 0 이상이어야 합니다.");
		if (rate.stripTrailingZeros().scale() > 0)
			throw new IllegalArgumentException("시간당 기본요금은 원 단위 정수여야 합니다(소수 불가): " + rate.toPlainString());
		if (rate.compareTo(MAX_HOURLY_RATE) > 0)
			throw new IllegalArgumentException("시간당 기본요금이 너무 큽니다(최대 " + MAX_HOURLY_RATE.toPlainString() + "원).");
		return rate.setScale(2, RoundingMode.UNNECESSARY);
	}

	/**
	 * 기본 금액 = 시간당 요금 × 이용 시간. 반올림 없이 소수 둘째 자리에 정확히 맞아야 한다(DDL CHECK base = rate × hours).
	 * NUMERIC(14,2) 를 넘으면 400.
	 */
	static BigDecimal baseAmount(BigDecimal hourlyRate, BigDecimal bookingHours) {
		BigDecimal product = hourlyRate.multiply(bookingHours);
		BigDecimal base;
		try {
			base = product.setScale(2, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException e) {
			throw new ConflictException("시간당 요금(" + hourlyRate.toPlainString() + ")이 원 단위가 아니어서 금액을 정확히 계산할 수 없습니다.");
		}
		if (base.compareTo(MAX_AMOUNT) > 0)
			throw new IllegalArgumentException("기본 금액(" + base.toPlainString() + ")이 너무 큽니다. 예약 기간을 줄이세요.");
		return base;
	}

	/** 할인 금액(없으면 0): 0 이상, 소수 둘째 자리까지, 기본 금액 이하. 위반 시 400 */
	static BigDecimal discountAmount(BigDecimal discount, BigDecimal base) {
		if (discount == null)
			return BigDecimal.ZERO.setScale(2);
		if (discount.signum() < 0)
			throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
		BigDecimal d;
		try {
			d = discount.setScale(2, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("할인 금액은 소수 둘째 자리까지만 입력할 수 있습니다: " + discount.toPlainString());
		}
		if (d.compareTo(base) > 0)
			throw new IllegalArgumentException(
					"할인 금액(" + d.toPlainString() + ")이 기본 금액(" + base.toPlainString() + ")보다 클 수 없습니다.");
		return d;
	}
}
