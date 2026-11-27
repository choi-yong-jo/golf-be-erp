package com.golf.erp.facility;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 부대시설·공간 종류·공간·시설 예약 API 요청/응답 */
public final class FacilityDtos {
	private FacilityDtos() {
	}

	static final String WHOLE_WON_MESSAGE = "시간당 기본요금은 원 단위 정수(소수 불가, 최대 10자리)여야 합니다.";

	// ---------------------------------------------------------------- 시설

	/** type: MEETING_ROOM, BANQUET_HALL. active 생략 시 true(운영 중) */
	public record FacilityCreateRequest(@NotNull Long golfCourseId, @NotBlank @Size(max = 30) String code,
			@NotBlank @Size(max = 150) String name, @NotNull FacilityType type, Boolean active) {
	}

	public record FacilityUpdateRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name,
			@NotNull FacilityType type) {
	}

	/** roomTypeCount·unitCount: 소속 공간 종류·공간 수(상태 무관) */
	public record FacilityResponse(Long id, Long golfCourseId, String code, String name, FacilityType type,
			boolean active, long roomTypeCount, long unitCount, LocalDateTime createdAt, LocalDateTime updatedAt,
			Long createdBy, Long updatedBy) {

		static FacilityResponse of(Facility f, long roomTypeCount, long unitCount) {
			return new FacilityResponse(f.getId(), f.getGolfCourseId(), f.getCode(), f.getName(), f.getType(),
					f.isActive(), roomTypeCount, unitCount, f.getCreatedAt(), f.getUpdatedAt(), f.getCreatedBy(),
					f.getUpdatedBy());
		}
	}

	// ---------------------------------------------------------------- 공간 종류

	/**
	 * roomTypeCode 는 시설 안에서 유일(대문자로 저장). defaultCapacity 생략 시 1. hourlyBaseRate 는 원 단위 정수.
	 * active 생략 시 true.
	 */
	public record RoomTypeCreateRequest(@NotNull Long facilityId, @NotBlank @Size(max = 40) String roomTypeCode,
			@NotBlank @Size(max = 100) String roomTypeName, @Positive Integer defaultCapacity,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 0, message = WHOLE_WON_MESSAGE) BigDecimal hourlyBaseRate,
			Boolean active) {
	}

	/** 전체 필드 수정. 요금을 바꿔도 이미 받은 예약의 요금 스냅샷은 그대로다. */
	public record RoomTypeUpdateRequest(@NotBlank @Size(max = 40) String roomTypeCode,
			@NotBlank @Size(max = 100) String roomTypeName, @NotNull @Positive Integer defaultCapacity,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 0, message = WHOLE_WON_MESSAGE) BigDecimal hourlyBaseRate) {
	}

	/** reason: 감사 로그에 남길 사유(선택) */
	public record ActiveRequest(@NotNull Boolean active, @Size(max = 500) String reason) {
	}

	/** unitCount: 이 종류의 공간 수(상태 무관) */
	public record RoomTypeResponse(Long id, Long facilityId, String facilityCode, String facilityName,
			FacilityType facilityType, Long golfCourseId, String roomTypeCode, String roomTypeName,
			Integer defaultCapacity, BigDecimal hourlyBaseRate, boolean active, long unitCount,
			LocalDateTime createdAt, LocalDateTime updatedAt, Long createdBy, Long updatedBy) {

		static RoomTypeResponse of(FacilityRoomType t, long unitCount) {
			Facility f = t.getFacility();
			return new RoomTypeResponse(t.getId(), f.getId(), f.getCode(), f.getName(), f.getType(),
					f.getGolfCourseId(), t.getCode(), t.getName(), t.getDefaultCapacity(), t.getHourlyBaseRate(),
					t.isActive(), unitCount, t.getCreatedAt(), t.getUpdatedAt(), t.getCreatedBy(), t.getUpdatedBy());
		}
	}

	// ---------------------------------------------------------------- 공간

	/**
	 * facilityRoomTypeId 는 같은 시설의 사용 중인 공간 종류. capacity 생략 시 공간 종류의 기본 수용 인원.
	 * 새 공간은 AVAILABLE 로 만든다.
	 */
	public record UnitCreateRequest(@NotNull Long facilityId, @NotNull Long facilityRoomTypeId,
			@NotBlank @Size(max = 40) String unitCode, @NotBlank @Size(max = 150) String unitName,
			@Positive Integer capacity) {
	}

	/** 시설은 바꿀 수 없다. facilityRoomTypeId 는 같은 시설의 종류만. capacity 생략 시 공간 종류의 기본 수용 인원. */
	public record UnitUpdateRequest(@NotNull Long facilityRoomTypeId, @NotBlank @Size(max = 40) String unitCode,
			@NotBlank @Size(max = 150) String unitName, @Positive Integer capacity) {
	}

	/** reason: 감사 로그에 남길 사유(선택) */
	public record UnitStatusRequest(@NotNull FacilityUnitStatus status, @Size(max = 500) String reason) {
	}

	/** hourlyRate: 공간 종류의 현재 시간당 기본요금(새 예약에 적용될 요금) */
	public record UnitResponse(Long id, Long facilityId, String facilityCode, String facilityName,
			FacilityType facilityType, Long golfCourseId, boolean facilityActive, Long facilityRoomTypeId,
			String roomTypeCode, String roomTypeName, boolean roomTypeActive, BigDecimal hourlyRate, String unitCode,
			String unitName, Integer capacity, FacilityUnitStatus status, LocalDateTime createdAt,
			LocalDateTime updatedAt, Long createdBy, Long updatedBy) {

		static UnitResponse of(FacilityUnit u) {
			Facility f = u.getFacility();
			FacilityRoomType t = u.getRoomType();
			return new UnitResponse(u.getId(), f.getId(), f.getCode(), f.getName(), f.getType(), f.getGolfCourseId(),
					f.isActive(), t.getId(), t.getCode(), t.getName(), t.isActive(), t.getHourlyBaseRate(),
					u.getUnitCode(), u.getUnitName(), u.getCapacity(), u.getStatus(), u.getCreatedAt(),
					u.getUpdatedAt(), u.getCreatedBy(), u.getUpdatedBy());
		}
	}

	/**
	 * 공간 상태 변경 결과. upcomingBookingCount: 이 공간에 남아 있는 입실 전(PENDING·CONFIRMED) 예약 수.
	 * 자동 취소하지 않으므로 warning 이 있으면 직원이 예약을 옮기거나 취소해야 한다.
	 */
	public record UnitStatusResponse(UnitResponse unit, long upcomingBookingCount, String warning) {
	}

	/**
	 * 예약 가능 공간. hourlyRate: 지금 예약하면 스냅샷될 시간당 요금, bookingHours: 조회 기간의 이용 시간,
	 * estimatedBaseAmount = hourlyRate × bookingHours.
	 */
	public record AvailableUnitResponse(UnitResponse unit, Long facilityRoomTypeId, String roomTypeName,
			BigDecimal hourlyRate, BigDecimal bookingHours, BigDecimal estimatedBaseAmount) {
	}

	// ---------------------------------------------------------------- 예약

	/**
	 * startAt/endAt: 15분 단위. guestName: memberId 가 있으면 생략 가능(회원 이름 사용). guestPhone 생략 시 회원 전화번호.
	 * guestCount 생략 시 1. discountAmount 생략 시 0. 기본 금액은 서버가 계산(공간 종류 요금 × 이용 시간).
	 * tentative=true 면 가예약(PENDING), 아니면 확정(CONFIRMED)으로 만든다.
	 */
	public record BookingCreateRequest(@NotNull Long facilityUnitId, Long memberId, @Size(max = 100) String guestName,
			@Size(max = 30) String guestPhone, @NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt,
			@Positive Integer guestCount, @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal discountAmount,
			@Size(max = 1000) String specialRequest, Boolean tentative) {
	}

	/**
	 * 전체 필드 수정. guestName 이 비어 있으면 회원 이름(회원 예약일 때), discountAmount 생략 시 0.
	 * 시간당 요금은 예약 때 스냅샷을 유지하고 이용 시간·기본 금액·합계만 다시 계산한다.
	 */
	public record BookingUpdateRequest(@Size(max = 100) String guestName, @Size(max = 30) String guestPhone,
			@NotNull LocalDateTime startAt, @NotNull LocalDateTime endAt, @NotNull @Positive Integer guestCount,
			@PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal discountAmount,
			@Size(max = 1000) String specialRequest) {
	}

	public record ReasonRequest(@Size(max = 500) String reason) {
	}

	/** memo: 감사 로그에 남길 메모(선택, 예: 카드 승인번호) */
	public record PaymentStatusRequest(@NotNull FacilityPaymentStatus paymentStatus, @Size(max = 500) String memo) {
	}

	/** roomTypeName: 공간의 현재 종류 이름. hourlyRate: 예약 시점 요금 스냅샷 */
	public record BookingResponse(Long id, String bookingNo, Long golfCourseId, Long facilityId, String facilityName,
			FacilityType facilityType, Long facilityRoomTypeId, String roomTypeName, Long facilityUnitId,
			String unitCode, String unitName, Long memberId, String guestName, String guestPhone,
			FacilityBookingStatus status, LocalDateTime startAt, LocalDateTime endAt, BigDecimal bookingHours,
			BigDecimal hourlyRate, Integer guestCount, LocalDateTime checkInAt, LocalDateTime checkOutAt,
			BigDecimal baseAmount, BigDecimal discountAmount, BigDecimal totalAmount,
			FacilityPaymentStatus paymentStatus, String specialRequest, LocalDateTime cancelledAt, String cancelReason,
			Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {

		static BookingResponse of(FacilityBooking b) {
			FacilityUnit u = b.getUnit();
			Facility f = u.getFacility();
			FacilityRoomType t = u.getRoomType();
			return new BookingResponse(b.getId(), b.getBookingNo(), f.getGolfCourseId(), f.getId(), f.getName(),
					f.getType(), t.getId(), t.getName(), u.getId(), u.getUnitCode(), u.getUnitName(), b.getMemberId(),
					b.getGuestName(), b.getGuestPhone(), b.getStatus(), b.getStartAt(), b.getEndAt(),
					b.getBookingHours(), b.getHourlyRate(), b.getGuestCount(), b.getCheckInAt(), b.getCheckOutAt(),
					b.getBaseAmount(), b.getDiscountAmount(), b.getTotalAmount(), b.getPaymentStatus(),
					b.getSpecialRequest(), b.getCancelledAt(), b.getCancelReason(), b.getCreatedBy(),
					b.getCreatedAt(), b.getUpdatedAt());
		}
	}

	// ---------------------------------------------------------------- 달력

	/** from~to(포함) 기간의 시설 공간별 점유 예약(PENDING·CONFIRMED·CHECKED_IN) */
	public record CalendarResponse(Long facilityId, String facilityCode, String facilityName, FacilityType facilityType,
			boolean facilityActive, LocalDate from, LocalDate to, List<CalendarUnit> units) {
	}

	public record CalendarUnit(Long unitId, String unitCode, String unitName, Long facilityRoomTypeId,
			String roomTypeName, Integer capacity, FacilityUnitStatus status, List<CalendarBooking> bookings) {
	}

	public record CalendarBooking(Long id, String bookingNo, FacilityBookingStatus status, LocalDateTime startAt,
			LocalDateTime endAt, BigDecimal bookingHours, String guestName, Integer guestCount,
			FacilityPaymentStatus paymentStatus) {

		static CalendarBooking of(FacilityBooking b) {
			return new CalendarBooking(b.getId(), b.getBookingNo(), b.getStatus(), b.getStartAt(), b.getEndAt(),
					b.getBookingHours(), b.getGuestName(), b.getGuestCount(), b.getPaymentStatus());
		}
	}
}
