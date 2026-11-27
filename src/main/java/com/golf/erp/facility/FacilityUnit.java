package com.golf.erp.facility;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.facility_unit. 실제 예약 대상 공간(회의실 1개, 연회홀 1개). 요금은 공간 종류({@link FacilityRoomType})의
 * 시간당 기본요금을 쓴다. 공간 종류는 반드시 같은 시설의 것이어야 한다(DDL 복합 FK (facility_id, facility_room_type_id)).
 * 같은 공간의 예약 겹침 검사는 이 행을 PESSIMISTIC_WRITE 로 잠근 뒤에 한다(DB 배타 제약이 없음).
 */
@Entity
@Table(name = "facility_unit")
public class FacilityUnit {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "facility_unit_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "facility_id", nullable = false, updatable = false)
	private Facility facility;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "facility_room_type_id", nullable = false)
	private FacilityRoomType roomType;
	@Column(name = "unit_code", nullable = false, length = 40)
	private String unitCode;
	@Column(name = "unit_name", nullable = false, length = 150)
	private String unitName;
	@Column(name = "capacity", nullable = false)
	private Integer capacity = 1;
	@Enumerated(EnumType.STRING)
	@Column(name = "unit_status", nullable = false, length = 20)
	private FacilityUnitStatus status = FacilityUnitStatus.AVAILABLE;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;

	protected FacilityUnit() {
	}

	public FacilityUnit(Facility facility, FacilityRoomType roomType, String unitCode, String unitName, int capacity,
			Long actor, LocalDateTime now) {
		this.facility = facility;
		this.createdAt = now;
		this.createdBy = actor;
		update(roomType, unitCode, unitName, capacity, actor, now);
	}

	/** 공간 종류는 같은 시설의 것만(아니면 400). */
	public void update(FacilityRoomType roomType, String unitCode, String unitName, int capacity, Long actor,
			LocalDateTime now) {
		if (!roomType.getFacility().getId().equals(facility.getId()))
			throw new IllegalArgumentException("공간 종류(" + roomType.getName() + ")가 이 공간의 시설(" + facility.getName()
					+ ")에 속하지 않습니다.");
		if (capacity <= 0)
			throw new IllegalArgumentException("수용 인원은 1명 이상이어야 합니다.");
		this.roomType = roomType;
		this.unitCode = unitCode;
		this.unitName = unitName;
		this.capacity = capacity;
		touch(actor, now);
	}

	public void changeStatus(FacilityUnitStatus status, Long actor, LocalDateTime now) {
		this.status = status;
		touch(actor, now);
	}

	/** 새 예약·기간 변경을 받을 수 있는지(시설 활성 + 공간 종류 사용 중 + 공간 AVAILABLE). 아니면 409 */
	void requireBookable() {
		if (!facility.isActive())
			throw new ConflictException("운영 중이 아닌 시설입니다: " + facility.getName());
		if (!roomType.isActive())
			throw new ConflictException("사용 중지된 공간 종류입니다: " + roomType.getName());
		if (status != FacilityUnitStatus.AVAILABLE)
			throw new ConflictException("예약을 받을 수 없는 상태의 공간입니다(" + status + "): " + unitName);
	}

	private void touch(Long actor, LocalDateTime now) {
		this.updatedAt = now;
		this.updatedBy = actor;
	}

	public Long getId() {
		return id;
	}

	public Facility getFacility() {
		return facility;
	}

	public FacilityRoomType getRoomType() {
		return roomType;
	}

	public String getUnitCode() {
		return unitCode;
	}

	public String getUnitName() {
		return unitName;
	}

	public Integer getCapacity() {
		return capacity;
	}

	public FacilityUnitStatus getStatus() {
		return status;
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
