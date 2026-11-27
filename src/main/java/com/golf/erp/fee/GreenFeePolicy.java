package com.golf.erp.fee;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * golf.green_fee_policy. 1인 기준 요금(baseFee)과 1인 할인액(discountAmount).
 * golfCourseId 가 null 이면 전체 골프장 공통 정책. timeFrom 포함, timeTo 미포함(null 이면 제한 없음).
 * 테이블에 updated_at 이 없다.
 */
@Entity
@Table(name = "green_fee_policy")
public class GreenFeePolicy {
	public static final String WEEKDAY = "WEEKDAY";
	public static final String WEEKEND = "WEEKEND";
	public static final String HOLIDAY = "HOLIDAY";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "green_fee_policy_id")
	private Long id;
	@Column(name = "golf_course_id")
	private Long golfCourseId;
	@Column(name = "policy_name", nullable = false, length = 150)
	private String policyName;
	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;
	@Column(name = "valid_to", nullable = false)
	private LocalDate validTo;
	@Column(name = "day_type", length = 20)
	private String dayType;
	@Column(name = "day_of_week")
	private Short dayOfWeek;
	@Column(name = "time_from")
	private LocalTime timeFrom;
	@Column(name = "time_to")
	private LocalTime timeTo;
	@Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal baseFee;
	@Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected GreenFeePolicy() {
	}

	public GreenFeePolicy(LocalDateTime now) {
		this.createdAt = now;
	}

	public void update(Long golfCourseId, String policyName, LocalDate validFrom, LocalDate validTo, String dayType,
			Short dayOfWeek, LocalTime timeFrom, LocalTime timeTo, BigDecimal baseFee, BigDecimal discountAmount,
			boolean active) {
		this.golfCourseId = golfCourseId;
		this.policyName = policyName;
		this.validFrom = validFrom;
		this.validTo = validTo;
		this.dayType = dayType;
		this.dayOfWeek = dayOfWeek;
		this.timeFrom = timeFrom;
		this.timeTo = timeTo;
		this.baseFee = baseFee;
		this.discountAmount = discountAmount;
		this.active = active;
	}

	public void deactivate() {
		this.active = false;
	}

	/** 날짜·요일·시간 조건이 맞는지 (활성·유효기간·골프장 조건은 조회 쿼리에서 거른다) */
	public boolean matches(LocalDate date, LocalTime time) {
		DayOfWeek dow = date.getDayOfWeek();
		if (dayOfWeek != null && dayOfWeek != dow.getValue())
			return false;
		if (dayType != null) {
			boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
			switch (dayType) {
			case WEEKDAY -> {
				if (weekend)
					return false;
			}
			case WEEKEND -> {
				if (!weekend)
					return false;
			}
			default -> {
				return false; // HOLIDAY 등: 공휴일 달력이 없어 자동 적용하지 않는다
			}
			}
		}
		if (timeFrom != null && time.isBefore(timeFrom))
			return false;
		return timeTo == null || time.isBefore(timeTo);
	}

	/** 1인 실결제 요금(기본 요금 - 할인) */
	public BigDecimal netFee() {
		return baseFee.subtract(discountAmount);
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public String getPolicyName() {
		return policyName;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getValidTo() {
		return validTo;
	}

	public String getDayType() {
		return dayType;
	}

	public Short getDayOfWeek() {
		return dayOfWeek;
	}

	public LocalTime getTimeFrom() {
		return timeFrom;
	}

	public LocalTime getTimeTo() {
		return timeTo;
	}

	public BigDecimal getBaseFee() {
		return baseFee;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public boolean isActive() {
		return active;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
