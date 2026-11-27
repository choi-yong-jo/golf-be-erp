package com.golf.erp.store;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.store_brand. 골프장에 입점하는 외부 브랜드(운영사). brand_code 는 대문자로 저장하고 바꾸지 않는다. */
@Entity
@Table(name = "store_brand")
public class StoreBrand {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_brand_id")
	private Long id;
	@Column(name = "brand_code", nullable = false, length = 40, updatable = false)
	private String brandCode;
	@Column(name = "brand_name", nullable = false, length = 150)
	private String brandName;
	@Column(name = "business_name", length = 200)
	private String businessName;
	@Column(name = "business_registration_no", length = 30)
	private String businessRegistrationNo;
	@Column(name = "contact_name", length = 100)
	private String contactName;
	@Column(name = "contact_phone", length = 30)
	private String contactPhone;
	@Column(name = "contact_email", length = 255)
	private String contactEmail;
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

	protected StoreBrand() {
	}

	public StoreBrand(String brandCode, Long actorId, LocalDateTime now) {
		this.brandCode = brandCode;
		this.createdBy = actorId;
		this.createdAt = now;
	}

	public void update(String brandName, String businessName, String businessRegistrationNo, String contactName,
			String contactPhone, String contactEmail, Long actorId, LocalDateTime now) {
		this.brandName = brandName;
		this.businessName = businessName;
		this.businessRegistrationNo = businessRegistrationNo;
		this.contactName = contactName;
		this.contactPhone = contactPhone;
		this.contactEmail = contactEmail;
		touch(actorId, now);
	}

	/** 사용 중지 가능 여부(영업 중 매장 유무)는 서비스가 먼저 확인한다. */
	public void changeActive(boolean active, Long actorId, LocalDateTime now) {
		this.active = active;
		touch(actorId, now);
	}

	private void touch(Long actorId, LocalDateTime now) {
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getBrandCode() {
		return brandCode;
	}

	public String getBrandName() {
		return brandName;
	}

	public String getBusinessName() {
		return businessName;
	}

	public String getBusinessRegistrationNo() {
		return businessRegistrationNo;
	}

	public String getContactName() {
		return contactName;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public String getContactEmail() {
		return contactEmail;
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
