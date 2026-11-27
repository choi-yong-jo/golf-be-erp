package com.golf.erp.equipment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 장비·정비 API 요청/응답 */
public final class EquipmentDtos {
	private EquipmentDtos() {
	}

	/** 등록·수정 공통. golfCourseId 를 비우면 공용 장비. type 은 자유 분류(예: MOWER, VEHICLE, IRRIGATION) */
	public record EquipmentRequest(Long golfCourseId, @NotBlank @Size(max = 40) String code,
			@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 40) String type,
			@Size(max = 100) String serialNo, LocalDate purchaseDate, @Size(max = 200) String location,
			@Size(max = 500) String memo) {
	}

	public record EquipmentStatusRequest(@NotNull EquipmentStatus status) {
	}

	public record EquipmentResponse(Long id, Long golfCourseId, String code, String name, String type,
			String serialNo, LocalDate purchaseDate, EquipmentStatus status, String location, String memo) {

		static EquipmentResponse of(Equipment e) {
			return new EquipmentResponse(e.getId(), e.getGolfCourseId(), e.getCode(), e.getName(), e.getType(),
					e.getSerialNo(), e.getPurchaseDate(), e.getStatus(), e.getLocation(), e.getMemo());
		}
	}

	/** type: 자유 분류(예: REPAIR, INSPECTION, PARTS) */
	public record MaintenanceRequest(@NotBlank @Size(max = 30) String type, @Size(max = 150) String vendorName,
			@Size(max = 1000) String description) {
	}

	/** cost 를 비우면 기존 값(기본 0) 유지, vendorName 을 비우면 기존 값 유지 */
	public record MaintenanceCompleteRequest(@PositiveOrZero @DecimalMax("999999999999.99") BigDecimal cost,
			@Size(max = 150) String vendorName) {
	}

	public record MaintenanceResponse(Long id, Long equipmentId, String equipmentCode, String equipmentName,
			String type, MaintenanceStatus status, LocalDateTime reportedAt, Long reportedBy,
			LocalDateTime startedAt, LocalDateTime completedAt, BigDecimal cost, String vendorName,
			String description) {

		static MaintenanceResponse of(EquipmentMaintenance m, Equipment e) {
			return new MaintenanceResponse(m.getId(), m.getEquipmentId(), e == null ? null : e.getCode(),
					e == null ? null : e.getName(), m.getType(), m.getStatus(), m.getReportedAt(), m.getReportedBy(),
					m.getStartedAt(), m.getCompletedAt(), m.getCost(), m.getVendorName(), m.getDescription());
		}
	}
}
