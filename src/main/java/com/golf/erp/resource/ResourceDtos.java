package com.golf.erp.resource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 캐디·카트 API 요청/응답 */
public final class ResourceDtos {
	private ResourceDtos() {
	}

	public record CaddieCreateRequest(@NotNull Long golfCourseId, @NotBlank @Size(max = 20) String caddieNo,
			@NotBlank @Size(max = 100) String name, @Size(max = 30) String phone, @Size(max = 10) String grade,
			LocalDate hiredOn, @Size(max = 500) String memo) {
	}

	public record CaddieUpdateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 30) String phone,
			@Size(max = 10) String grade, LocalDate hiredOn, @Size(max = 500) String memo) {
	}

	public record CaddieStatusRequest(@NotNull CaddieStatus status) {
	}

	/** assignedRoundId: 지금 배정된 라운드(없으면 null) */
	public record CaddieResponse(Long id, Long golfCourseId, String caddieNo, String name, String phone, String grade,
			CaddieStatus status, LocalDate hiredOn, String memo, Long assignedRoundId, LocalDateTime createdAt,
			LocalDateTime updatedAt) {

		static CaddieResponse of(Caddie c, Long assignedRoundId) {
			return new CaddieResponse(c.getId(), c.getGolfCourseId(), c.getCaddieNo(), c.getName(), c.getPhone(),
					c.getGrade(), c.getStatus(), c.getHiredOn(), c.getMemo(), assignedRoundId, c.getCreatedAt(),
					c.getUpdatedAt());
		}
	}

	public record CartCreateRequest(@NotNull Long golfCourseId, @NotBlank @Size(max = 20) String cartNo,
			@Min(1) @Max(10) Short seatCount, LocalDate purchaseDate, @Size(max = 500) String memo) {
	}

	public record CartUpdateRequest(@NotNull @Min(1) @Max(10) Short seatCount, LocalDate purchaseDate,
			@Size(max = 500) String memo) {
	}

	public record CartStatusRequest(@NotNull CartStatus status) {
	}

	public record CartResponse(Long id, Long golfCourseId, String cartNo, Short seatCount, CartStatus status,
			LocalDate purchaseDate, LocalDateTime lastMaintenanceAt, String memo, Long assignedRoundId,
			LocalDateTime createdAt, LocalDateTime updatedAt) {

		static CartResponse of(Cart c, Long assignedRoundId) {
			return new CartResponse(c.getId(), c.getGolfCourseId(), c.getCartNo(), c.getSeatCount(), c.getStatus(),
					c.getPurchaseDate(), c.getLastMaintenanceAt(), c.getMemo(), assignedRoundId, c.getCreatedAt(),
					c.getUpdatedAt());
		}
	}
}
