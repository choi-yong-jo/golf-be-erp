package com.golf.erp.facility;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.facility. 회의실·연회장 부대시설 마스터. 그 아래에 공간 종류({@link FacilityRoomType})와
 * 실제 예약 단위인 공간({@link FacilityUnit})이 있다.
 * 이력이 남아야 하므로 삭제하지 않고 비활성(is_active=false)으로 바꾼다.
 */
@Entity
@Table(name = "facility")
public class Facility {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "facility_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false, updatable = false)
	private Long golfCourseId;
	@Column(name = "facility_code", nullable = false, length = 30)
	private String code;
	@Column(name = "facility_name", nullable = false, length = 150)
	private String name;
	@Enumerated(EnumType.STRING)
	@Column(name = "facility_type", nullable = false, length = 20)
	private FacilityType type;
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

	protected Facility() {
	}

	public Facility(Long golfCourseId, String code, String name, FacilityType type, boolean active, Long actor,
			LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.code = code;
		this.name = name;
		this.type = type;
		this.active = active;
		this.createdAt = now;
		this.createdBy = actor;
		touch(actor, now);
	}

	public void update(String code, String name, FacilityType type, Long actor, LocalDateTime now) {
		this.code = code;
		this.name = name;
		this.type = type;
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
		this.updatedAt = now;
		this.updatedBy = actor;
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

	public FacilityType getType() {
		return type;
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
