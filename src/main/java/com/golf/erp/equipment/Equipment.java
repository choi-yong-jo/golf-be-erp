package com.golf.erp.equipment;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * golf.equipment. 골프장이 보유한 개별 장비·자산(잔디깎이, 장비 단품 등). golf_course_id 가 없으면 공용 장비.
 * RETIRED(폐기)는 마지막 상태라 다른 상태로 바꿀 수 없다.
 */
@Entity
@Table(name = "equipment")
public class Equipment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "equipment_id")
	private Long id;
	@Column(name = "golf_course_id")
	private Long golfCourseId;
	@Column(name = "equipment_code", nullable = false, unique = true, length = 40)
	private String code;
	@Column(name = "equipment_name", nullable = false, length = 150)
	private String name;
	@Column(name = "equipment_type", nullable = false, length = 40)
	private String type;
	@Column(name = "serial_no", length = 100)
	private String serialNo;
	@Column(name = "purchase_date")
	private LocalDate purchaseDate;
	@Enumerated(EnumType.STRING)
	@Column(name = "equipment_status", nullable = false, length = 20)
	private EquipmentStatus status = EquipmentStatus.AVAILABLE;
	@Column(name = "location", length = 200)
	private String location;
	@Column(name = "memo", length = 500)
	private String memo;

	protected Equipment() {
	}

	public Equipment(String code) {
		this.code = code;
	}

	public void update(Long golfCourseId, String code, String name, String type, String serialNo,
			LocalDate purchaseDate, String location, String memo) {
		requireNotRetired();
		this.golfCourseId = golfCourseId;
		this.code = code;
		this.name = name;
		this.type = type;
		this.serialNo = serialNo;
		this.purchaseDate = purchaseDate;
		this.location = location;
		this.memo = memo;
	}

	public void changeStatus(EquipmentStatus status) {
		requireNotRetired();
		this.status = status;
	}

	public void requireNotRetired() {
		if (status == EquipmentStatus.RETIRED)
			throw new ConflictException("폐기(RETIRED)된 장비입니다: " + code);
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

	public String getType() {
		return type;
	}

	public String getSerialNo() {
		return serialNo;
	}

	public LocalDate getPurchaseDate() {
		return purchaseDate;
	}

	public EquipmentStatus getStatus() {
		return status;
	}

	public String getLocation() {
		return location;
	}

	public String getMemo() {
		return memo;
	}
}
