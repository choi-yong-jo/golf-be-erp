package com.golf.erp.lodging;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * golf.lodging_booking. 객실 예약 한 행 = 대실 1회 또는 1박(DDL ck_lodging_booking_mode). 연박은 박마다 행을 만든다.
 * <p>
 * DDL CHECK 를 엔티티에서 먼저 지킨다:
 * 예약 시간 규칙은 {@link LodgingSlot}, base_amount = rate_amount, 0 ≤ discount ≤ base, total = base − discount,
 * CHECKED_IN ⇒ actual_check_in_at, CHECKED_OUT ⇒ actual_check_in_at·actual_check_out_at,
 * actual_check_out_at ≥ actual_check_in_at.
 */
@Entity
@Table(name = "lodging_booking")
public class LodgingBooking {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "lodging_booking_id")
	private Long id;
	@Column(name = "booking_no", nullable = false, unique = true, length = 40)
	private String bookingNo;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "lodging_room_id", nullable = false, updatable = false)
	private LodgingRoom room;
	@Column(name = "member_id", updatable = false)
	private Long memberId;
	@Column(name = "guest_name", nullable = false, length = 100)
	private String guestName;
	@Column(name = "guest_phone", length = 30)
	private String guestPhone;
	@Enumerated(EnumType.STRING)
	@Column(name = "booking_type", nullable = false, length = 20, updatable = false)
	private LodgingBookingType bookingType;
	@Column(name = "scheduled_check_in_at", nullable = false)
	private LocalDateTime scheduledCheckInAt;
	@Column(name = "scheduled_check_out_at", nullable = false)
	private LocalDateTime scheduledCheckOutAt;
	@Column(name = "actual_check_in_at")
	private LocalDateTime actualCheckInAt;
	@Column(name = "actual_check_out_at")
	private LocalDateTime actualCheckOutAt;
	@Column(name = "stay_nights", nullable = false)
	private Short stayNights = 0;
	@Column(name = "day_use_hours", nullable = false)
	private Short dayUseHours = 0;
	@Column(name = "rate_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal rateAmount = BigDecimal.ZERO;
	@Column(name = "base_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal baseAmount = BigDecimal.ZERO;
	@Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal discountAmount = BigDecimal.ZERO;
	@Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal totalAmount = BigDecimal.ZERO;
	@Enumerated(EnumType.STRING)
	@Column(name = "booking_status", nullable = false, length = 20)
	private LodgingBookingStatus status = LodgingBookingStatus.CONFIRMED;
	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false, length = 20)
	private LodgingPaymentStatus paymentStatus = LodgingPaymentStatus.UNPAID;
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

	protected LodgingBooking() {
	}

	/**
	 * 저장 전 bookingNo 는 {@code DocumentNo.temporary()} 를 넣고, saveAndFlush 뒤 {@link #assignNo} 로 바꾼다.
	 * rateAmount 는 예약 시점의 객실 타입 요금(스냅샷)이다. 이후 타입 요금이 바뀌어도 이 예약 금액은 그대로다.
	 */
	public LodgingBooking(String temporaryNo, LodgingRoom room, Long memberId, LodgingBookingStatus status,
			LodgingSlot slot, BigDecimal rateAmount, Long createdBy, LocalDateTime now) {
		if (status != LodgingBookingStatus.PENDING && status != LodgingBookingStatus.CONFIRMED)
			throw new IllegalArgumentException("예약은 PENDING 또는 CONFIRMED 로만 만들 수 있습니다.");
		if (rateAmount.signum() < 0)
			throw new IllegalArgumentException("요금은 0 이상이어야 합니다.");
		this.bookingNo = temporaryNo;
		this.room = room;
		this.memberId = memberId;
		this.status = status;
		this.bookingType = slot.type();
		this.rateAmount = rateAmount;
		this.baseAmount = rateAmount;
		this.totalAmount = rateAmount;
		applySlot(slot);
		this.createdBy = createdBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void assignNo(String bookingNo) {
		this.bookingNo = bookingNo;
	}

	/** 손님·이용 시간·할인·요청 사항. 입실 전(PENDING·CONFIRMED)만. 요금(rate=base)은 등록 시 값을 유지한다. */
	public void changeDetails(String guestName, String guestPhone, LodgingSlot slot, BigDecimal discountAmount,
			String specialRequest, LocalDateTime now) {
		requireEditable();
		if (slot.type() != bookingType)
			throw new IllegalArgumentException("예약 유형(대실·1박)은 바꿀 수 없습니다. 취소 후 다시 예약하세요.");
		if (discountAmount.signum() < 0)
			throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
		if (discountAmount.compareTo(baseAmount) > 0)
			throw new IllegalArgumentException("할인 금액(" + discountAmount + ")이 기본 금액(" + baseAmount + ")보다 클 수 없습니다.");
		this.guestName = guestName;
		this.guestPhone = guestPhone;
		applySlot(slot);
		this.discountAmount = discountAmount;
		this.totalAmount = baseAmount.subtract(discountAmount);
		this.specialRequest = specialRequest;
		this.updatedAt = now;
	}

	private void applySlot(LodgingSlot slot) {
		this.scheduledCheckInAt = slot.checkIn();
		this.scheduledCheckOutAt = slot.checkOut();
		this.stayNights = slot.stayNights();
		this.dayUseHours = slot.dayUseHours();
	}

	/** PENDING → CONFIRMED */
	public void confirm(LocalDateTime now) {
		requireStatus(LodgingBookingStatus.PENDING, "확정");
		this.status = LodgingBookingStatus.CONFIRMED;
		this.updatedAt = now;
	}

	/** CONFIRMED → CHECKED_IN. 예약일(입실 예정일) 0시부터, 퇴실 예정 시각 전까지 가능하다. */
	public void checkIn(LocalDateTime now) {
		requireStatus(LodgingBookingStatus.CONFIRMED, "입실 처리");
		if (now.toLocalDate().isBefore(scheduledCheckInAt.toLocalDate()))
			throw new ConflictException("예약일(" + scheduledCheckInAt.toLocalDate() + ") 전에는 입실 처리할 수 없습니다.");
		if (!now.isBefore(scheduledCheckOutAt))
			throw new ConflictException("퇴실 예정 시각(" + scheduledCheckOutAt + ")이 지났습니다. 노쇼 또는 취소로 처리하세요.");
		this.status = LodgingBookingStatus.CHECKED_IN;
		this.actualCheckInAt = now;
		this.updatedAt = now;
	}

	/** CHECKED_IN → CHECKED_OUT. 시계가 되돌아가도 actual_check_out_at ≥ actual_check_in_at 을 지킨다. */
	public void checkOut(LocalDateTime now) {
		requireStatus(LodgingBookingStatus.CHECKED_IN, "퇴실 처리");
		this.status = LodgingBookingStatus.CHECKED_OUT;
		this.actualCheckOutAt = now.isBefore(actualCheckInAt) ? actualCheckInAt : now;
		this.updatedAt = now;
	}

	/** PENDING·CONFIRMED → CANCELLED */
	public void cancel(String reason, LocalDateTime now) {
		if (!LodgingBookingStatus.UPCOMING.contains(status))
			throw new ConflictException("입실 전(PENDING·CONFIRMED) 예약만 취소할 수 있습니다. 현재 상태: " + status);
		this.status = LodgingBookingStatus.CANCELLED;
		this.cancelledAt = now;
		this.cancelReason = reason;
		this.updatedAt = now;
	}

	/** CONFIRMED → NO_SHOW. 입실 예정 시각이 지난 뒤에만 가능하다. */
	public void markNoShow(LocalDateTime now) {
		requireStatus(LodgingBookingStatus.CONFIRMED, "노쇼 처리");
		if (now.isBefore(scheduledCheckInAt))
			throw new ConflictException("입실 예정 시각(" + scheduledCheckInAt + ") 전에는 노쇼로 처리할 수 없습니다.");
		this.status = LodgingBookingStatus.NO_SHOW;
		this.updatedAt = now;
	}

	/** 수납 상태 기록. REFUNDED 는 돈을 받은 적이 있는(PARTIAL·PAID) 경우만. */
	public void changePaymentStatus(LodgingPaymentStatus paymentStatus, LocalDateTime now) {
		if (paymentStatus == LodgingPaymentStatus.REFUNDED && this.paymentStatus != LodgingPaymentStatus.PARTIAL
				&& this.paymentStatus != LodgingPaymentStatus.PAID)
			throw new ConflictException("받은 금액이 없는 예약(" + this.paymentStatus + ")은 환불 상태로 바꿀 수 없습니다.");
		this.paymentStatus = paymentStatus;
		this.updatedAt = now;
	}

	void requireEditable() {
		if (!LodgingBookingStatus.UPCOMING.contains(status))
			throw new ConflictException("입실 전(PENDING·CONFIRMED) 예약만 수정할 수 있습니다. 현재 상태: " + status);
	}

	private void requireStatus(LodgingBookingStatus expected, String action) {
		if (status != expected)
			throw new ConflictException(expected + " 상태의 예약만 " + action + "할 수 있습니다. 현재 상태: " + status);
	}

	public Long getId() {
		return id;
	}

	public String getBookingNo() {
		return bookingNo;
	}

	public LodgingRoom getRoom() {
		return room;
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

	public LodgingBookingType getBookingType() {
		return bookingType;
	}

	public LocalDateTime getScheduledCheckInAt() {
		return scheduledCheckInAt;
	}

	public LocalDateTime getScheduledCheckOutAt() {
		return scheduledCheckOutAt;
	}

	public LocalDateTime getActualCheckInAt() {
		return actualCheckInAt;
	}

	public LocalDateTime getActualCheckOutAt() {
		return actualCheckOutAt;
	}

	public Short getStayNights() {
		return stayNights;
	}

	public Short getDayUseHours() {
		return dayUseHours;
	}

	public BigDecimal getRateAmount() {
		return rateAmount;
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

	public LodgingBookingStatus getStatus() {
		return status;
	}

	public LodgingPaymentStatus getPaymentStatus() {
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
