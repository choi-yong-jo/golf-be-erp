package com.golf.erp.store;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * golf.tenant_store. 골프장 안에서 외부(또는 직영) 운영하는 식당·카페. PK 컬럼은 store_id.
 * 골프장과 매장 코드는 등록 후 바꾸지 않는다(uq_tenant_store_code). 폐점(CLOSED)한 매장은 수정·상태 변경 불가.
 */
@Entity
@Table(name = "tenant_store")
public class TenantStore {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false, updatable = false)
	private Long golfCourseId;
	@Column(name = "store_brand_id")
	private Long storeBrandId;
	@Column(name = "store_code", nullable = false, length = 40, updatable = false)
	private String storeCode;
	@Column(name = "store_name", nullable = false, length = 150)
	private String storeName;
	@Enumerated(EnumType.STRING)
	@Column(name = "store_type", nullable = false, length = 20)
	private StoreType storeType;
	@Column(name = "location", length = 200)
	private String location;
	@Column(name = "lease_start_date")
	private LocalDate leaseStartDate;
	@Column(name = "lease_end_date")
	private LocalDate leaseEndDate;
	@Column(name = "monthly_rent", precision = 14, scale = 2)
	private BigDecimal monthlyRent;
	@Enumerated(EnumType.STRING)
	@Column(name = "store_status", nullable = false, length = 20)
	private StoreStatus status = StoreStatus.ACTIVE;
	@Column(name = "is_external_tenant", nullable = false)
	private boolean externalTenant = true;
	@Column(name = "manager_name", length = 100)
	private String managerName;
	@Column(name = "manager_phone", length = 30)
	private String managerPhone;
	@Column(name = "memo", length = 500)
	private String memo;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TenantStore() {
	}

	public TenantStore(Long golfCourseId, String storeCode, Long actorId, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.storeCode = storeCode;
		this.createdBy = actorId;
		this.createdAt = now;
	}

	/** 기본 정보와 임대 정보 전체 수정. 임대 종료일 ≥ 시작일, 월 임대료 ≥ 0 (DDL CHECK 와 같은 규칙). */
	public void update(Long storeBrandId, String storeName, StoreType storeType, String location,
			LocalDate leaseStartDate, LocalDate leaseEndDate, BigDecimal monthlyRent, boolean externalTenant,
			String managerName, String managerPhone, String memo, Long actorId, LocalDateTime now) {
		if (status == StoreStatus.CLOSED)
			throw new ConflictException("폐점한 매장은 수정할 수 없습니다: " + storeCode);
		if (leaseStartDate != null && leaseEndDate != null && leaseEndDate.isBefore(leaseStartDate))
			throw new IllegalArgumentException("임대 종료일은 임대 시작일보다 빠를 수 없습니다.");
		if (monthlyRent != null && monthlyRent.signum() < 0)
			throw new IllegalArgumentException("월 임대료는 0 이상이어야 합니다.");
		this.storeBrandId = storeBrandId;
		this.storeName = storeName;
		this.storeType = storeType;
		this.location = location;
		this.leaseStartDate = leaseStartDate;
		this.leaseEndDate = leaseEndDate;
		this.monthlyRent = monthlyRent == null ? null : monthlyRent.setScale(2, RoundingMode.HALF_UP);
		this.externalTenant = externalTenant;
		this.managerName = managerName;
		this.managerPhone = managerPhone;
		this.memo = memo;
		touch(actorId, now);
	}

	/** CLOSED 는 최종 상태라 다른 상태로 바꿀 수 없다. */
	public void changeStatus(StoreStatus status, Long actorId, LocalDateTime now) {
		if (this.status == StoreStatus.CLOSED)
			throw new ConflictException("폐점한 매장은 상태를 바꿀 수 없습니다: " + storeCode);
		this.status = status;
		touch(actorId, now);
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

	public Long getStoreBrandId() {
		return storeBrandId;
	}

	public String getStoreCode() {
		return storeCode;
	}

	public String getStoreName() {
		return storeName;
	}

	public StoreType getStoreType() {
		return storeType;
	}

	public String getLocation() {
		return location;
	}

	public LocalDate getLeaseStartDate() {
		return leaseStartDate;
	}

	public LocalDate getLeaseEndDate() {
		return leaseEndDate;
	}

	public BigDecimal getMonthlyRent() {
		return monthlyRent;
	}

	public StoreStatus getStatus() {
		return status;
	}

	public boolean isExternalTenant() {
		return externalTenant;
	}

	public String getManagerName() {
		return managerName;
	}

	public String getManagerPhone() {
		return managerPhone;
	}

	public String getMemo() {
		return memo;
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
