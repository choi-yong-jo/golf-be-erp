package com.golf.erp.facility;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.facility_room_type. 시설 안의 공간 종류(예: 소회의실, 대연회홀). 시간당 기본요금과 기본 수용 인원을 가진다.
 * 공간({@link FacilityUnit})은 같은 시설의 종류만 가질 수 있다(DDL 복합 FK). 예약 요금은 이 종류의 시간당 기본요금을
 * 예약 시점에 스냅샷으로 복사한다.
 * <p>
 * 시간당 기본요금은 원 단위 정수만 받는다({@link FacilityPricing#hourlyRate}). 그래야 요금 × 시간(소수 둘째 자리)이
 * 소수 둘째 자리 안에서 정확히 떨어져 DDL CHECK(base_amount = hourly_rate * booking_hours)를 지킨다.
 */
@Entity
@Table(name = "facility_room_type")
public class FacilityRoomType {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "facility_room_type_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "facility_id", nullable = false, updatable = false)
	private Facility facility;
	@Column(name = "room_type_code", nullable = false, length = 40)
	private String code;
	@Column(name = "room_type_name", nullable = false, length = 100)
	private String name;
	@Column(name = "default_capacity", nullable = false)
	private Integer defaultCapacity = 1;
	@Column(name = "hourly_base_rate", nullable = false, precision = 12, scale = 2)
	private BigDecimal hourlyBaseRate = BigDecimal.ZERO.setScale(2);
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected FacilityRoomType() {
	}

	public FacilityRoomType(Facility facility, String code, String name, int defaultCapacity,
			BigDecimal hourlyBaseRate, boolean active, Long actor, LocalDateTime now) {
		this.facility = facility;
		this.active = active;
		this.createdBy = actor;
		this.createdAt = now;
		update(code, name, defaultCapacity, hourlyBaseRate, actor, now);
	}

	/** hourlyBaseRate 는 {@link FacilityPricing#hourlyRate} 로 검증·정리된 값이어야 한다. */
	public void update(String code, String name, int defaultCapacity, BigDecimal hourlyBaseRate, Long actor,
			LocalDateTime now) {
		if (defaultCapacity <= 0)
			throw new IllegalArgumentException("기본 수용 인원은 1명 이상이어야 합니다.");
		this.code = code;
		this.name = name;
		this.defaultCapacity = defaultCapacity;
		this.hourlyBaseRate = FacilityPricing.hourlyRate(hourlyBaseRate);
		touch(actor, now);
	}

	public void activate(Long actor, LocalDateTime now) {
		this.active = true;
		touch(actor, now);
	}

	public void deactivate(Long actor, LocalDateTime now) {
		this.active = false;
		touch(actor, now);
	}

	private void touch(Long actor, LocalDateTime now) {
		this.updatedBy = actor;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Facility getFacility() {
		return facility;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public Integer getDefaultCapacity() {
		return defaultCapacity;
	}

	public BigDecimal getHourlyBaseRate() {
		return hourlyBaseRate;
	}

	public boolean isActive() {
		return active;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
