package com.golf.erp.equipment;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** golf.equipment_maintenance. 장비 정비 요청·이력. OPEN → IN_PROGRESS → COMPLETED, 끝나기 전에는 CANCELLED 가능 */
@Entity
@Table(name = "equipment_maintenance")
public class EquipmentMaintenance {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "maintenance_id")
	private Long id;
	@Column(name = "equipment_id", nullable = false)
	private Long equipmentId;
	@Column(name = "maintenance_type", nullable = false, length = 30)
	private String type;
	@Column(name = "reported_at", nullable = false)
	private LocalDateTime reportedAt;
	@Column(name = "started_at")
	private LocalDateTime startedAt;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Column(name = "cost", nullable = false, precision = 14, scale = 2)
	private BigDecimal cost = BigDecimal.ZERO;
	@Column(name = "vendor_name", length = 150)
	private String vendorName;
	@Column(name = "description", length = 1000)
	private String description;
	@Enumerated(EnumType.STRING)
	@Column(name = "maintenance_status", nullable = false, length = 20)
	private MaintenanceStatus status = MaintenanceStatus.OPEN;
	@Column(name = "reported_by")
	private Long reportedBy;

	protected EquipmentMaintenance() {
	}

	public EquipmentMaintenance(Long equipmentId, Long reportedBy, LocalDateTime now) {
		this.equipmentId = equipmentId;
		this.reportedBy = reportedBy;
		this.reportedAt = now;
	}

	public void update(String type, String vendorName, String description) {
		requireActive("수정");
		this.type = type;
		this.vendorName = vendorName;
		this.description = description;
	}

	public void start(LocalDateTime now) {
		if (status != MaintenanceStatus.OPEN)
			throw new ConflictException("접수(OPEN) 상태의 정비만 시작할 수 있습니다. 현재: " + status);
		this.status = MaintenanceStatus.IN_PROGRESS;
		this.startedAt = now;
	}

	/** 시작하지 않고 바로 완료하면 시작 시각도 완료 시각으로 남긴다. */
	public void complete(BigDecimal cost, String vendorName, LocalDateTime now) {
		requireActive("완료");
		if (cost != null)
			this.cost = cost.setScale(2, RoundingMode.HALF_UP);
		if (vendorName != null)
			this.vendorName = vendorName;
		if (startedAt == null)
			this.startedAt = now;
		this.completedAt = now;
		this.status = MaintenanceStatus.COMPLETED;
	}

	public void cancel() {
		requireActive("취소");
		this.status = MaintenanceStatus.CANCELLED;
	}

	private void requireActive(String action) {
		if (status.isClosed())
			throw new ConflictException("이미 끝난 정비는 " + action + "할 수 없습니다. 현재: " + status);
	}

	public Long getId() {
		return id;
	}

	public Long getEquipmentId() {
		return equipmentId;
	}

	public String getType() {
		return type;
	}

	public LocalDateTime getReportedAt() {
		return reportedAt;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public String getVendorName() {
		return vendorName;
	}

	public String getDescription() {
		return description;
	}

	public MaintenanceStatus getStatus() {
		return status;
	}

	public Long getReportedBy() {
		return reportedBy;
	}
}
