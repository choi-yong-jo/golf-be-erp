package com.golf.erp.lodging;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.lodging_room_type. 골프텔 객실 타입(정원·대실 시간·대실/1박 기본 요금).
 * <p>
 * DDL CHECK 를 엔티티에서 먼저 지킨다: capacity &gt; 0, day_use_hours &gt; 0, 요금 ≥ 0.
 * 골프장·타입 코드는 등록 뒤 바꾸지 않는다(uq_lodging_room_type).
 */
@Entity
@Table(name = "lodging_room_type")
public class LodgingRoomType {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "lodging_room_type_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false, updatable = false)
	private Long golfCourseId;
	@Column(name = "room_type_code", nullable = false, length = 30, updatable = false)
	private String code;
	@Column(name = "room_type_name", nullable = false, length = 100)
	private String name;
	@Column(name = "capacity", nullable = false)
	private Integer capacity = 2;
	@Column(name = "day_use_hours", nullable = false)
	private Short dayUseHours = 3;
	@Column(name = "day_use_base_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal dayUseBaseFee = BigDecimal.ZERO;
	@Column(name = "overnight_base_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal overnightBaseFee = BigDecimal.ZERO;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;

	protected LodgingRoomType() {
	}

	public LodgingRoomType(Long golfCourseId, String code, Long actorId, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.code = code;
		this.createdBy = actorId;
		this.updatedBy = actorId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 이름·정원·대실 시간·요금. 이미 받은 예약의 금액·시간은 예약에 저장된 값을 그대로 쓴다. */
	public void update(String name, int capacity, short dayUseHours, BigDecimal dayUseBaseFee,
			BigDecimal overnightBaseFee, Long actorId, LocalDateTime now) {
		if (capacity <= 0)
			throw new IllegalArgumentException("정원은 1명 이상이어야 합니다.");
		if (dayUseHours <= 0)
			throw new IllegalArgumentException("대실 시간은 1시간 이상이어야 합니다.");
		if (dayUseBaseFee.signum() < 0 || overnightBaseFee.signum() < 0)
			throw new IllegalArgumentException("요금은 0 이상이어야 합니다.");
		this.name = name;
		this.capacity = capacity;
		this.dayUseHours = dayUseHours;
		this.dayUseBaseFee = dayUseBaseFee;
		this.overnightBaseFee = overnightBaseFee;
		touch(actorId, now);
	}

	public void activate(Long actorId, LocalDateTime now) {
		this.active = true;
		touch(actorId, now);
	}

	public void deactivate(Long actorId, LocalDateTime now) {
		this.active = false;
		touch(actorId, now);
	}

	/** 유형별 기본 1회(대실 1회·1박) 요금 */
	public BigDecimal rateFor(LodgingBookingType type) {
		return type == LodgingBookingType.DAY_USE ? dayUseBaseFee : overnightBaseFee;
	}

	private void touch(Long actorId, LocalDateTime now) {
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public Integer getCapacity() {
		return capacity;
	}

	public Short getDayUseHours() {
		return dayUseHours;
	}

	public BigDecimal getDayUseBaseFee() {
		return dayUseBaseFee;
	}

	public BigDecimal getOvernightBaseFee() {
		return overnightBaseFee;
	}

	public boolean isActive() {
		return active;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}
}
