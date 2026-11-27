package com.golf.erp.store;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** 입점 브랜드·입점 매장 API 요청/응답 */
public final class StoreDtos {
	/** 앞뒤 공백 허용, 비어 있거나 숫자 묶음을 하이픈으로 이은 형식(예: 123-45-67890) */
	static final String BUSINESS_NO_REGEX = "\\s*([0-9]+(-[0-9]+)*)?\\s*";
	static final String BUSINESS_NO_MESSAGE = "사업자등록번호는 숫자와 하이픈만 쓸 수 있습니다(예: 123-45-67890).";

	private StoreDtos() {
	}

	// ---- 입점 브랜드 ----

	public record StoreBrandCreateRequest(
			@NotBlank @Size(max = 40) @Pattern(regexp = "\\s*[A-Za-z0-9_-]+\\s*", message = "브랜드 코드는 영문·숫자·_·- 만 쓸 수 있습니다.") String brandCode,
			@NotBlank @Size(max = 150) String brandName, @Size(max = 200) String businessName,
			@Size(max = 30) @Pattern(regexp = BUSINESS_NO_REGEX, message = BUSINESS_NO_MESSAGE) String businessRegistrationNo,
			@Size(max = 100) String contactName, @Size(max = 30) String contactPhone,
			@Email @Size(max = 255) String contactEmail) {
	}

	/** 브랜드 코드는 바꿀 수 없다. */
	public record StoreBrandUpdateRequest(@NotBlank @Size(max = 150) String brandName,
			@Size(max = 200) String businessName,
			@Size(max = 30) @Pattern(regexp = BUSINESS_NO_REGEX, message = BUSINESS_NO_MESSAGE) String businessRegistrationNo,
			@Size(max = 100) String contactName, @Size(max = 30) String contactPhone,
			@Email @Size(max = 255) String contactEmail) {
	}

	public record ActiveRequest(@NotNull Boolean active) {
	}

	/** storeCounts: 상태별 매장 수(모든 상태 포함, 없으면 0), totalStores: 전체 매장 수 */
	public record StoreBrandResponse(Long id, String brandCode, String brandName, String businessName,
			String businessRegistrationNo, String contactName, String contactPhone, String contactEmail,
			boolean active, Map<StoreStatus, Long> storeCounts, long totalStores, Long createdBy, Long updatedBy,
			LocalDateTime createdAt, LocalDateTime updatedAt) {

		static StoreBrandResponse of(StoreBrand b, Map<StoreStatus, Long> counts) {
			long total = counts.values().stream().mapToLong(Long::longValue).sum();
			return new StoreBrandResponse(b.getId(), b.getBrandCode(), b.getBrandName(), b.getBusinessName(),
					b.getBusinessRegistrationNo(), b.getContactName(), b.getContactPhone(), b.getContactEmail(),
					b.isActive(), counts, total, b.getCreatedBy(), b.getUpdatedBy(), b.getCreatedAt(),
					b.getUpdatedAt());
		}
	}

	// ---- 입점 매장 ----

	/** externalTenant 생략 시 true(외부 운영). 등록 시 상태는 ACTIVE. */
	public record TenantStoreCreateRequest(@NotNull Long golfCourseId, Long storeBrandId,
			@NotBlank @Size(max = 40) String storeCode, @NotBlank @Size(max = 150) String storeName,
			@NotNull StoreType storeType, @Size(max = 200) String location, LocalDate leaseStartDate,
			LocalDate leaseEndDate, @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal monthlyRent,
			Boolean externalTenant, @Size(max = 100) String managerName, @Size(max = 30) String managerPhone,
			@Size(max = 500) String memo) {
	}

	/** 골프장·매장 코드는 바꿀 수 없다. 임대 정보(기간·월 임대료)도 여기서 함께 수정한다. */
	public record TenantStoreUpdateRequest(Long storeBrandId, @NotBlank @Size(max = 150) String storeName,
			@NotNull StoreType storeType, @Size(max = 200) String location, LocalDate leaseStartDate,
			LocalDate leaseEndDate, @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal monthlyRent,
			@NotNull Boolean externalTenant, @Size(max = 100) String managerName,
			@Size(max = 30) String managerPhone, @Size(max = 500) String memo) {
	}

	/** reason: 감사 로그에 남길 사유(선택) */
	public record StoreStatusRequest(@NotNull StoreStatus status, @Size(max = 500) String reason) {
	}

	/** leaseDaysRemaining: 오늘부터 임대 종료일까지 남은 일수(지났으면 음수, 종료일 없으면 null) */
	public record TenantStoreResponse(Long id, Long golfCourseId, String golfCourseName, Long storeBrandId,
			String storeBrandName, String storeCode, String storeName, StoreType storeType, String location,
			LocalDate leaseStartDate, LocalDate leaseEndDate, Long leaseDaysRemaining, BigDecimal monthlyRent,
			StoreStatus status, boolean externalTenant, String managerName, String managerPhone, String memo,
			Long createdBy, Long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {

		static TenantStoreResponse of(TenantStore s, String golfCourseName, String storeBrandName, LocalDate today) {
			Long remaining = s.getLeaseEndDate() == null ? null
					: ChronoUnit.DAYS.between(today, s.getLeaseEndDate());
			return new TenantStoreResponse(s.getId(), s.getGolfCourseId(), golfCourseName, s.getStoreBrandId(),
					storeBrandName, s.getStoreCode(), s.getStoreName(), s.getStoreType(), s.getLocation(),
					s.getLeaseStartDate(), s.getLeaseEndDate(), remaining, s.getMonthlyRent(), s.getStatus(),
					s.isExternalTenant(), s.getManagerName(), s.getManagerPhone(), s.getMemo(), s.getCreatedBy(),
					s.getUpdatedBy(), s.getCreatedAt(), s.getUpdatedAt());
		}
	}
}
