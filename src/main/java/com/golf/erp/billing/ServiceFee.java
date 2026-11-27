package com.golf.erp.billing;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.service_fee — 부대 요금표(캐디피·카트비·대여료 등). 그린피는 여기 두지 않는다(티타임·그린피 정책).
 * hole_basis: 9·18·27·36 (NULL 이면 홀 수와 무관). 삭제 대신 is_active=false 로 내린다.
 */
@Entity
@Table(name = "service_fee")
public class ServiceFee {
	public static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "service_fee_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Enumerated(EnumType.STRING)
	@Column(name = "fee_type", nullable = false, length = 20)
	private FeeType feeType;
	@Column(name = "fee_name", nullable = false, length = 100)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(name = "charge_unit", nullable = false, length = 10)
	private ChargeUnit chargeUnit = ChargeUnit.TEAM;
	@Column(name = "hole_basis")
	private Short holeBasis;
	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;
	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom;
	@Column(name = "valid_to", nullable = false)
	private LocalDate validTo = OPEN_END;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected ServiceFee() {
	}

	public ServiceFee(Long golfCourseId, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.createdAt = now;
	}

	public void update(FeeType feeType, String name, ChargeUnit chargeUnit, Short holeBasis, BigDecimal amount,
			LocalDate validFrom, LocalDate validTo, boolean active, LocalDateTime now) {
		if (validTo.isBefore(validFrom))
			throw new IllegalArgumentException("적용 종료일은 시작일 이후여야 합니다.");
		this.feeType = feeType;
		this.name = name;
		this.chargeUnit = chargeUnit;
		this.holeBasis = holeBasis;
		this.amount = amount;
		this.validFrom = validFrom;
		this.validTo = validTo;
		this.active = active;
		this.updatedAt = now;
	}

	public void deactivate(LocalDateTime now) {
		this.active = false;
		this.updatedAt = now;
	}

	/** 사용 중이면서 그 날짜가 적용 기간 안인지 */
	public boolean appliesOn(LocalDate date) {
		return active && !date.isBefore(validFrom) && !date.isAfter(validTo);
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public FeeType getFeeType() {
		return feeType;
	}

	public String getName() {
		return name;
	}

	public ChargeUnit getChargeUnit() {
		return chargeUnit;
	}

	public Short getHoleBasis() {
		return holeBasis;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getValidFrom() {
		return validFrom;
	}

	public LocalDate getValidTo() {
		return validTo;
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
}
