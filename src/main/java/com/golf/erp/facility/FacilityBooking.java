package com.golf.erp.facility;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.facility_booking. 회의실·연회장 공간 예약. 예약 기간은 반열린 구간 [start, end) 이고 15분 단위다.
 * <p>
 * 요금은 예약 시점 공간 종류의 시간당 기본요금을 hourly_rate 에 스냅샷으로 보관한다. 기간을 바꿔도 스냅샷은 그대로이고
 * 이용 시간·기본 금액·합계만 다시 계산한다. DDL CHECK 를 엔티티에서 먼저 지킨다:
 * booking_hours = (end − start) 시간, base = hourly_rate × booking_hours, 0 ≤ discount ≤ base, total = base − discount,
 * guest_count &gt; 0, CHECKED_IN ⇒ check_in_at, CHECKED_OUT ⇒ check_in_at·check_out_at, check_out_at ≥ check_in_at.
 */
@Entity
@Table(name = "facility_booking")
public class FacilityBooking {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "facility_booking_id")
	private Long id;
	@Column(name = "booking_no", nullable = false, unique = true, length = 40)
	private String bookingNo;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "facility_unit_id", nullable = false, updatable = false)
	private FacilityUnit unit;
	@Column(name = "member_id", updatable = false)
	private Long memberId;
	@Column(name = "guest_name", nullable = false, length = 100)
	private String guestName;
	@Column(name = "guest_phone", length = 30)
	private String guestPhone;
	@Column(name = "booking_start_at", nullable = false)
	private LocalDateTime startAt;
	@Column(name = "booking_end_at", nullable = false)
	private LocalDateTime endAt;
	@Column(name = "booking_hours", nullable = false, precision = 6, scale = 2)
	private BigDecimal bookingHours;
	@Column(name = "hourly_rate", nullable = false, precision = 12, scale = 2, updatable = false)
	private BigDecimal hourlyRate;
	@Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal baseAmount;
	@Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);
	@Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalAmount;
	@Column(name = "guest_count", nullable = false)
	private Integer guestCount = 1;
	@Enumerated(EnumType.STRING)
	@Column(name = "booking_status", nullable = false, length = 20)
	private FacilityBookingStatus status = FacilityBookingStatus.CONFIRMED;
	@Column(name = "check_in_at")
	private LocalDateTime checkInAt;
	@Column(name = "check_out_at")
	private LocalDateTime checkOutAt;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private FacilityPaymentStatus paymentStatus = FacilityPaymentStatus.UNPAID;
	@Column(name = "special_request", length = 1000)
	private String specialRequest;
	@Column(name = "cancelled_at")
	private LocalDateTime cancelledAt;
	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected FacilityBooking() {
	}

	/**
	 * 저장 전 bookingNo 는 {@code DocumentNo.temporary()} 를 넣고, saveAndFlush 뒤 {@link #assignNo} 로 바꾼다.
	 * 저장 전에 반드시 {@link #changeDetails} 로 기간·금액을 채운다. hourlyRate 는 공간 종류 요금 스냅샷(원 단위).
	 */
	public FacilityBooking(String temporaryNo, FacilityUnit unit, Long memberId, FacilityBookingStatus status,
			BigDecimal hourlyRate, Long createdBy, LocalDateTime now) {
		if (status != FacilityBookingStatus.PENDING && status != FacilityBookingStatus.CONFIRMED)
			throw new IllegalArgumentException("예약은 PENDING 또는 CONFIRMED 로만 만들 수 있습니다.");
		this.bookingNo = temporaryNo;
		this.unit = unit;
		this.memberId = memberId;
		this.status = status;
		this.hourlyRate = FacilityPricing.hourlyRate(hourlyRate);
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void assignNo(String bookingNo) {
		this.bookingNo = bookingNo;
	}

	/**
	 * 손님·기간·인원·할인·요청 사항. 입실 전(PENDING·CONFIRMED)만 바꿀 수 있다. 이용 시간·기본 금액·합계는 기간과
	 * 시간당 요금 스냅샷으로 다시 계산한다.
	 */
	public void changeDetails(String guestName, String guestPhone, LocalDateTime startAt, LocalDateTime endAt,
			int guestCount, BigDecimal discountAmount, String specialRequest, LocalDateTime now) {
		requireEditable();
		if (guestCount <= 0)
			throw new IllegalArgumentException("이용 인원은 1명 이상이어야 합니다.");
		BigDecimal hours = FacilityPricing.bookingHours(startAt, endAt);
		BigDecimal base = FacilityPricing.baseAmount(hourlyRate, hours);
		BigDecimal discount = FacilityPricing.discountAmount(discountAmount, base);
		this.guestName = guestName;
		this.guestPhone = guestPhone;
		this.startAt = startAt;
		this.endAt = endAt;
		this.bookingHours = hours;
		this.guestCount = guestCount;
		this.baseAmount = base;
		this.discountAmount = discount;
		this.totalAmount = base.subtract(discount);
		this.specialRequest = specialRequest;
		this.updatedAt = now;
	}

	/** PENDING → CONFIRMED */
	public void confirm(LocalDateTime now) {
		requireStatus(FacilityBookingStatus.PENDING, "확정");
		this.status = FacilityBookingStatus.CONFIRMED;
		this.updatedAt = now;
	}

	/** CONFIRMED → CHECKED_IN. 예약일(시작일) 전에는 입실할 수 없고, 예약 기간이 끝났으면 입실할 수 없다. */
	public void checkIn(LocalDateTime now) {
		requireStatus(FacilityBookingStatus.CONFIRMED, "입실 처리");
		if (now.toLocalDate().isBefore(startAt.toLocalDate()))
			throw new ConflictException("예약일(" + startAt.toLocalDate() + ") 전에는 입실 처리할 수 없습니다.");
		if (!now.isBefore(endAt))
			throw new ConflictException("예약 기간이 이미 끝났습니다. 노쇼 또는 취소로 처리하세요.");
		this.status = FacilityBookingStatus.CHECKED_IN;
		this.checkInAt = now;
		this.updatedAt = now;
	}

	/** CHECKED_IN → CHECKED_OUT. 시계가 되돌아가도 check_out_at ≥ check_in_at 을 지킨다. */
	public void checkOut(LocalDateTime now) {
		requireStatus(FacilityBookingStatus.CHECKED_IN, "퇴실 처리");
		this.status = FacilityBookingStatus.CHECKED_OUT;
		this.checkOutAt = now.isBefore(checkInAt) ? checkInAt : now;
		this.updatedAt = now;
	}

	/** PENDING·CONFIRMED → CANCELLED */
	public void cancel(String reason, LocalDateTime now) {
		if (status != FacilityBookingStatus.PENDING && status != FacilityBookingStatus.CONFIRMED)
			throw new ConflictException("입실 전(PENDING·CONFIRMED) 예약만 취소할 수 있습니다. 현재 상태: " + status);
		this.status = FacilityBookingStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancelReason = reason;
		this.updatedAt = now;
	}

	/** CONFIRMED → NO_SHOW. 예약 시작 시각이 지난 뒤에만 가능하다. */
	public void markNoShow(LocalDateTime now) {
		requireStatus(FacilityBookingStatus.CONFIRMED, "노쇼 처리");
		if (now.isBefore(startAt))
			throw new ConflictException("예약 시작 시각(" + startAt + ") 전에는 노쇼로 처리할 수 없습니다.");
		this.status = FacilityBookingStatus.NO_SHOW;
		this.updatedAt = now;
	}

	/** 수납 상태 기록. REFUNDED 는 돈을 받은 적이 있는(PARTIAL·PAID) 경우만. */
	public void changePaymentStatus(FacilityPaymentStatus paymentStatus, LocalDateTime now) {
		if (paymentStatus == FacilityPaymentStatus.REFUNDED && this.paymentStatus != FacilityPaymentStatus.PARTIAL
				&& this.paymentStatus != FacilityPaymentStatus.PAID)
			throw new ConflictException("받은 금액이 없는 예약(" + this.paymentStatus + ")은 환불 상태로 바꿀 수 없습니다.");
		this.paymentStatus = paymentStatus;
		this.updatedAt = now;
	}

	/** 내용 수정은 입실 전(PENDING·CONFIRMED)만. 아니면 409 */
	void requireEditable() {
		if (status != FacilityBookingStatus.PENDING && status != FacilityBookingStatus.CONFIRMED)
			throw new ConflictException("입실 전(PENDING·CONFIRMED) 예약만 수정할 수 있습니다. 현재 상태: " + status);
	}

	private void requireStatus(FacilityBookingStatus expected, String action) {
		if (status != expected)
			throw new ConflictException(expected + " 상태의 예약만 " + action + "할 수 있습니다. 현재 상태: " + status);
	}

	public Long getId() {
		return id;
	}

	public String getBookingNo() {
		return bookingNo;
	}

	public FacilityUnit getUnit() {
		return unit;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getGuestName() {
		return guestName;
	}

	public String getGuestPhone() {
		return guestPhone;
	}

	public FacilityBookingStatus getStatus() {
		return status;
	}

	public LocalDateTime getStartAt() {
		return startAt;
	}

	public LocalDateTime getEndAt() {
		return endAt;
	}

	public BigDecimal getBookingHours() {
		return bookingHours;
	}

	public BigDecimal getHourlyRate() {
		return hourlyRate;
	}

	public Integer getGuestCount() {
		return guestCount;
	}

	public LocalDateTime getCheckInAt() {
		return checkInAt;
	}

	public LocalDateTime getCheckOutAt() {
		return checkOutAt;
	}

	public BigDecimal getBaseAmount() {
		return baseAmount;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public FacilityPaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public String getSpecialRequest() {
		return specialRequest;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
