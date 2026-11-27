package com.golf.erp.lodging;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** 숙박(골프텔) API 요청/응답. 금액은 원 단위 정수(소수 불가). */
public final class LodgingDtos {
	private LodgingDtos() {
	}

	// ---------------------------------------------------------------- 객실 타입

	/** roomTypeCode 는 대문자로 저장한다. 생략한 값은 DDL 기본값(정원 2, 대실 3시간, 요금 0). */
	public record RoomTypeCreateRequest(@NotNull Long golfCourseId,
			@NotBlank @Size(max = 30) @Pattern(regexp = "^\\s*[A-Za-z0-9_-]+\\s*$", message = "영문·숫자·_·- 만 쓸 수 있습니다.") String roomTypeCode,
			@NotBlank @Size(max = 100) String roomTypeName, @Min(1) @Max(100) Integer capacity,
			@Min(1) @Max(LodgingSlot.MAX_DAY_USE_HOURS) Short dayUseHours,
			@PositiveOrZero @Digits(integer = 10, fraction = 0) BigDecimal dayUseBaseFee,
			@PositiveOrZero @Digits(integer = 10, fraction = 0) BigDecimal overnightBaseFee) {
	}

	public record RoomTypeUpdateRequest(@NotBlank @Size(max = 100) String roomTypeName,
			@NotNull @Min(1) @Max(100) Integer capacity,
			@NotNull @Min(1) @Max(LodgingSlot.MAX_DAY_USE_HOURS) Short dayUseHours,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 0) BigDecimal dayUseBaseFee,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 0) BigDecimal overnightBaseFee) {
	}

	/** roomCount: 이 타입의 객실 수(상태 무관) */
	public record RoomTypeResponse(Long id, Long golfCourseId, String roomTypeCode, String roomTypeName,
			Integer capacity, Short dayUseHours, BigDecimal dayUseBaseFee, BigDecimal overnightBaseFee, boolean active,
			long roomCount, LocalDateTime createdAt, LocalDateTime updatedAt, Long createdBy, Long updatedBy) {

		static RoomTypeResponse of(LodgingRoomType t, long roomCount) {
			return new RoomTypeResponse(t.getId(), t.getGolfCourseId(), t.getCode(), t.getName(), t.getCapacity(),
					t.getDayUseHours(), t.getDayUseBaseFee(), t.getOvernightBaseFee(), t.isActive(), roomCount,
					t.getCreatedAt(), t.getUpdatedAt(), t.getCreatedBy(), t.getUpdatedBy());
		}
	}

	/** 상세: 상태별 객실 수(모든 상태 키 포함) */
	public record RoomTypeDetailResponse(RoomTypeResponse roomType, Map<LodgingRoomStatus, Long> roomCountByStatus) {
	}

	// ---------------------------------------------------------------- 객실

	public record RoomCreateRequest(@NotNull Long roomTypeId, @NotBlank @Size(max = 40) String roomCode,
			@Size(max = 100) String roomName) {
	}

	/** roomTypeId 를 바꾸면 같은 골프장의 타입이어야 하고, 이 객실에 끝나지 않은 예약이 없어야 한다. */
	public record RoomUpdateRequest(@NotNull Long roomTypeId, @NotBlank @Size(max = 40) String roomCode,
			@Size(max = 100) String roomName) {
	}

	public record RoomStatusRequest(@NotNull LodgingRoomStatus status) {
	}

	public record RoomResponse(Long id, Long golfCourseId, Long roomTypeId, String roomTypeCode, String roomTypeName,
			boolean roomTypeActive, Integer capacity, String roomCode, String roomName, LodgingRoomStatus status,
			LocalDateTime createdAt, LocalDateTime updatedAt, Long createdBy, Long updatedBy) {

		static RoomResponse of(LodgingRoom r) {
			LodgingRoomType t = r.getRoomType();
			return new RoomResponse(r.getId(), t.getGolfCourseId(), t.getId(), t.getCode(), t.getName(), t.isActive(),
					t.getCapacity(), r.getRoomCode(), r.getRoomName(), r.getStatus(), r.getCreatedAt(),
					r.getUpdatedAt(), r.getCreatedBy(), r.getUpdatedBy());
		}
	}

	/**
	 * 객실 상세·상태 변경 응답. checkedInBookingNo: 지금 입실 중인 예약(없으면 null),
	 * upcomingBookingCount: 입실 전(PENDING·CONFIRMED)이고 아직 끝나지 않은 예약 수(보수·운영 중지로 바꿀 때 안내용).
	 */
	public record RoomDetailResponse(RoomResponse room, String checkedInBookingNo, long upcomingBookingCount) {
	}

	/** 가용 객실 한 칸과 그 조건의 예상 금액(rateAmount × nights). 할인 전 금액이다. */
	public record AvailableRoomResponse(RoomResponse room, LodgingBookingType bookingType,
			LocalDateTime scheduledCheckInAt, LocalDateTime scheduledCheckOutAt, short nights, short dayUseHours,
			BigDecimal rateAmount, BigDecimal totalAmount) {
	}

	// ---------------------------------------------------------------- 예약

	/**
	 * 예약 등록.
	 * <ul>
	 * <li>DAY_USE: date + checkInTime 입실, dayUseHours(생략 시 객실 타입 값) 뒤 퇴실. nights 는 생략 또는 1.</li>
	 * <li>OVERNIGHT: date 16:00 입실 → 다음 날 11:00 퇴실. nights(1~14, 기본 1)만큼 연속 1박 예약 행을 만든다.</li>
	 * </ul>
	 * guestName 은 memberId 가 없으면 필수(있으면 비었을 때 회원 이름·전화로 채움). guestCount 는 저장하지 않고 정원 확인에만 쓴다.
	 * discountAmount 는 예약 행(1박·대실 1회)마다 적용된다. tentative=true 면 PENDING(가예약), 아니면 CONFIRMED.
	 */
	public record BookingCreateRequest(@NotNull Long lodgingRoomId, @NotNull LodgingBookingType bookingType,
			@NotNull LocalDate date, LocalTime checkInTime, @Min(1) @Max(LodgingSlot.MAX_DAY_USE_HOURS) Short dayUseHours,
			@Min(1) @Max(LodgingBookingService.MAX_NIGHTS) Integer nights, Long memberId,
			@Size(max = 100) String guestName, @Size(max = 30) String guestPhone, @Min(1) Integer guestCount,
			@PositiveOrZero @Digits(integer = 12, fraction = 0) BigDecimal discountAmount,
			@Size(max = 1000) String specialRequest, Boolean tentative) {
	}

	/**
	 * 예약 수정(입실 전만). 객실·예약 유형은 바꿀 수 없다(취소 후 재예약). 요금(rate=base)은 등록 시 값을 유지한다.
	 * DAY_USE 는 checkInTime 필수, dayUseHours 생략 시 현재 값 유지. OVERNIGHT 는 date 의 1박으로 옮긴다.
	 */
	public record BookingUpdateRequest(@NotNull LocalDate date, LocalTime checkInTime,
			@Min(1) @Max(LodgingSlot.MAX_DAY_USE_HOURS) Short dayUseHours, @Size(max = 100) String guestName,
			@Size(max = 30) String guestPhone, @Min(1) Integer guestCount,
			@PositiveOrZero @Digits(integer = 12, fraction = 0) BigDecimal discountAmount,
			@Size(max = 1000) String specialRequest) {
	}

	public record CancelRequest(@Size(max = 500) String reason) {
	}

	public record PaymentStatusRequest(@NotNull LodgingPaymentStatus paymentStatus, @Size(max = 500) String memo) {
	}

	public record BookingResponse(Long id, String bookingNo, Long golfCourseId, Long roomTypeId, String roomTypeCode,
			String roomTypeName, Long lodgingRoomId, String roomCode, String roomName, Long memberId, String guestName,
			String guestPhone, LodgingBookingType bookingType, LocalDateTime scheduledCheckInAt,
			LocalDateTime scheduledCheckOutAt, LocalDateTime actualCheckInAt, LocalDateTime actualCheckOutAt,
			Short stayNights, Short dayUseHours, BigDecimal rateAmount, BigDecimal baseAmount,
			BigDecimal discountAmount, BigDecimal totalAmount, LodgingBookingStatus status,
			LodgingPaymentStatus paymentStatus, String specialRequest, LocalDateTime cancelledAt, String cancelReason,
			Long createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {

		static BookingResponse of(LodgingBooking b) {
			LodgingRoom r = b.getRoom();
			LodgingRoomType t = r.getRoomType();
			return new BookingResponse(b.getId(), b.getBookingNo(), t.getGolfCourseId(), t.getId(), t.getCode(),
					t.getName(), r.getId(), r.getRoomCode(), r.getRoomName(), b.getMemberId(), b.getGuestName(),
					b.getGuestPhone(), b.getBookingType(), b.getScheduledCheckInAt(), b.getScheduledCheckOutAt(),
					b.getActualCheckInAt(), b.getActualCheckOutAt(), b.getStayNights(), b.getDayUseHours(),
					b.getRateAmount(), b.getBaseAmount(), b.getDiscountAmount(), b.getTotalAmount(), b.getStatus(),
					b.getPaymentStatus(), b.getSpecialRequest(), b.getCancelledAt(), b.getCancelReason(),
					b.getCreatedBy(), b.getCreatedAt(), b.getUpdatedAt());
		}
	}

	/** 등록 결과. 연박이면 박마다 한 행(bookings), totalAmount 는 그 합계 */
	public record BookingCreateResponse(int count, BigDecimal totalAmount, List<BookingResponse> bookings) {
	}

	// ---------------------------------------------------------------- 달력

	public record CalendarBooking(Long id, String bookingNo, LodgingBookingType bookingType, String guestName,
			LocalDateTime scheduledCheckInAt, LocalDateTime scheduledCheckOutAt, LodgingBookingStatus status,
			LodgingPaymentStatus paymentStatus, BigDecimal totalAmount) {

		static CalendarBooking of(LodgingBooking b) {
			return new CalendarBooking(b.getId(), b.getBookingNo(), b.getBookingType(), b.getGuestName(),
					b.getScheduledCheckInAt(), b.getScheduledCheckOutAt(), b.getStatus(), b.getPaymentStatus(),
					b.getTotalAmount());
		}
	}

	public record CalendarRoom(Long lodgingRoomId, String roomCode, String roomName, Long roomTypeId,
			String roomTypeCode, String roomTypeName, LodgingRoomStatus status, List<CalendarBooking> bookings) {
	}

	public record CalendarResponse(Long golfCourseId, Long roomTypeId, LocalDate from, LocalDate to,
			List<CalendarRoom> rooms) {
	}
}
