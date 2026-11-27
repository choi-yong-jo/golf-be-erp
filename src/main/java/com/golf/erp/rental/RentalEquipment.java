package com.golf.erp.rental;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.rental_equipment. 대여 품목 마스터(예: 남성용 드라이버 R, 골프화 260). 골프장별 수량은 rental_equipment_stock.
 * equipment_code 는 바꿀 수 없다(재고·렌탈 이력이 코드로 조회되므로).
 */
@Entity
@Table(name = "rental_equipment")
public class RentalEquipment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "rental_equipment_id")
	private Long id;
	@Column(name = "equipment_code", nullable = false, length = 40, updatable = false)
	private String code;
	@Column(name = "equipment_name", nullable = false, length = 150)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(name = "equipment_category", nullable = false, length = 30)
	private EquipmentCategory category;
	@Column(name = "brand", length = 100)
	private String brand;
	@Column(name = "model_name", length = 100)
	private String modelName;
	@Column(name = "size_label", length = 30)
	private String sizeLabel;
	@Column(name = "description", length = 1000)
	private String description;
	@Column(name = "daily_rental_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal dailyRentalFee = BigDecimal.ZERO;
	@Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal depositAmount = BigDecimal.ZERO;
	@Column(name = "is_rentable", nullable = false)
	private boolean rentable = true;
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

	protected RentalEquipment() {
	}

	public RentalEquipment(String code, Long actor, LocalDateTime now) {
		this.code = code;
		this.createdBy = actor;
		this.createdAt = now;
		touch(actor, now);
	}

	/** 수정자·수정 시각 기록. 변경 메서드를 부른 뒤 서비스가 호출한다. */
	public void touch(Long actor, LocalDateTime now) {
		this.updatedBy = actor;
		this.updatedAt = now;
	}

	public void update(String name, EquipmentCategory category, String brand, String modelName, String sizeLabel,
			String description, BigDecimal dailyRentalFee, BigDecimal depositAmount, boolean rentable) {
		this.name = name;
		this.category = category;
		this.brand = brand;
		this.modelName = modelName;
		this.sizeLabel = sizeLabel;
		this.description = description;
		this.dailyRentalFee = dailyRentalFee;
		this.depositAmount = depositAmount;
		this.rentable = rentable;
	}

	public void changeFlags(boolean active, boolean rentable) {
		this.active = active;
		this.rentable = active && rentable;
	}

	/** 비활성화하면 대여도 막는다. */
	public void deactivate() {
		changeFlags(false, false);
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	/** 새 렌탈 예약·출고가 가능한지 */
	public boolean isAvailableForRent() {
		return active && rentable;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public EquipmentCategory getCategory() {
		return category;
	}

	public String getBrand() {
		return brand;
	}

	public String getModelName() {
		return modelName;
	}

	public String getSizeLabel() {
		return sizeLabel;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getDailyRentalFee() {
		return dailyRentalFee;
	}

	public BigDecimal getDepositAmount() {
		return depositAmount;
	}

	public boolean isRentable() {
		return rentable;
	}

	public boolean isActive() {
		return active;
	}

	/** 오류 메시지용 표시 이름 */
	String label() {
		return name + "(" + code + ")";
	}
}
