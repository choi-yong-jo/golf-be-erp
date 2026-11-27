package com.golf.erp.rental;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * golf.equipment_rental. 렌탈 한 건(접수·출고·반납). 품목은 {@link EquipmentRentalItem}(같은 모듈 부모-자식).
 * 금액 합계(rental_fee_total, deposit_total, damage_fee_total)는 품목이 바뀔 때마다 {@link #recomputeTotals()} 로 다시 계산한다.
 */
@Entity
@Table(name = "equipment_rental")
public class EquipmentRental {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "equipment_rental_id")
	private Long id;
	@Column(name = "rental_no", nullable = false, length = 40)
	private String rentalNo;
	@Column(name = "golf_course_id", nullable = false, updatable = false)
	private Long golfCourseId;
	@Column(name = "reservation_id")
	private Long reservationId;
	@Column(name = "round_id")
	private Long roundId;
	@Column(name = "member_id")
	private Long memberId;
	@Column(name = "renter_name", nullable = false, length = 100)
	private String renterName;
	@Column(name = "renter_phone", length = 30)
	private String renterPhone;
	@Enumerated(EnumType.STRING)
	@Column(name = "rental_status", nullable = false, length = 20)
	private RentalStatus status = RentalStatus.RESERVED;
	@Column(name = "rented_at")
	private LocalDateTime rentedAt;
	@Column(name = "due_at")
	private LocalDateTime dueAt;
	@Column(name = "returned_at")
	private LocalDateTime returnedAt;
	@Column(name = "rental_fee_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal rentalFeeTotal = BigDecimal.ZERO;
	@Column(name = "deposit_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal depositTotal = BigDecimal.ZERO;
	@Column(name = "damage_fee_total", nullable = false, precision = 14, scale = 2)
	private BigDecimal damageFeeTotal = BigDecimal.ZERO;
	@Column(name = "received_by")
	private Long receivedBy;
	@Column(name = "returned_to")
	private Long returnedTo;
	@Column(name = "memo", length = 500)
	private String memo;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "rental", cascade = CascadeType.ALL)
	@OrderBy("id")
	private List<EquipmentRentalItem> items = new ArrayList<>();

	protected EquipmentRental() {
	}

	public EquipmentRental(Long golfCourseId, Long reservationId, Long roundId, Long memberId, String renterName,
			String renterPhone, String memo, String temporaryNo, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.reservationId = reservationId;
		this.roundId = roundId;
		this.memberId = memberId;
		this.renterName = renterName;
		this.renterPhone = renterPhone;
		this.memo = memo;
		this.rentalNo = temporaryNo;
		this.createdAt = now;
	}

	void assignNo(String rentalNo) {
		this.rentalNo = rentalNo;
	}

	EquipmentRentalItem addItem(Long rentalEquipmentId, Long equipmentId, int quantity, BigDecimal dailyRentalFee,
			int rentalDays, BigDecimal depositAmount, String conditionOut) {
		requireStatus(RentalStatus.RESERVED, "품목을 추가");
		EquipmentRentalItem item = new EquipmentRentalItem(this, rentalEquipmentId, equipmentId, quantity,
				dailyRentalFee, rentalDays, depositAmount, conditionOut);
		items.add(item);
		recomputeTotals();
		return item;
	}

	/** 출고: RESERVED → RENTED */
	void issue(LocalDateTime rentedAt, LocalDateTime dueAt, Long receivedBy) {
		requireStatus(RentalStatus.RESERVED, "출고");
		if (dueAt.isBefore(rentedAt))
			throw new IllegalArgumentException("반납 기한은 대여 시각 이후여야 합니다.");
		this.status = RentalStatus.RENTED;
		this.rentedAt = rentedAt;
		this.dueAt = dueAt;
		this.receivedBy = receivedBy;
	}

	/** 반납 처리 뒤 상태 갱신. 모든 품목이 처리되면 RETURNED, 아니면 기한 경과 여부에 따라 OVERDUE / PARTIALLY_RETURNED */
	void afterReturn(Long returnedTo, LocalDateTime now) {
		this.returnedTo = returnedTo;
		boolean done = items.stream().allMatch(i -> i.outstandingQuantity() == 0);
		if (done) {
			this.status = RentalStatus.RETURNED;
			this.returnedAt = now;
		} else {
			this.status = isPastDue(now) ? RentalStatus.OVERDUE : RentalStatus.PARTIALLY_RETURNED;
		}
	}

	/** 반납 기한 연장. 새 기한이 지금 이후면 OVERDUE 를 해제한다. */
	void extend(LocalDateTime newDueAt, LocalDateTime now) {
		if (!status.isOut())
			throw new ConflictException("대여 중인 렌탈만 반납 기한을 연장할 수 있습니다. (현재 상태: " + status + ")");
		if (rentedAt != null && newDueAt.isBefore(rentedAt))
			throw new IllegalArgumentException("반납 기한은 대여 시각 이후여야 합니다.");
		if (dueAt != null && !newDueAt.isAfter(dueAt))
			throw new IllegalArgumentException("새 반납 기한은 현재 기한(" + dueAt + ") 이후여야 합니다.");
		this.dueAt = newDueAt;
		if (status == RentalStatus.OVERDUE && newDueAt.isAfter(now))
			this.status = hasAnyReturned() ? RentalStatus.PARTIALLY_RETURNED : RentalStatus.RENTED;
	}

	/** 취소: 출고 전(RESERVED)만 가능 */
	void cancel() {
		requireStatus(RentalStatus.RESERVED, "취소");
		this.status = RentalStatus.CANCELLED;
		items.forEach(EquipmentRentalItem::cancel);
		recomputeTotals();
	}

	/** 기한이 지났으면 OVERDUE 로 바꾸고 true */
	boolean markOverdueIfPastDue(LocalDateTime now) {
		if ((status == RentalStatus.RENTED || status == RentalStatus.PARTIALLY_RETURNED) && isPastDue(now)) {
			this.status = RentalStatus.OVERDUE;
			return true;
		}
		return false;
	}

	/** 취소된 품목을 뺀 합계. 대여료 = Σ 일 대여료 × 대여 일수 × 수량, 보증금 = Σ 보증금 × 수량, 파손료 = Σ 파손료 */
	void recomputeTotals() {
		BigDecimal fee = BigDecimal.ZERO;
		BigDecimal deposit = BigDecimal.ZERO;
		BigDecimal damage = BigDecimal.ZERO;
		for (EquipmentRentalItem i : items) {
			if (i.getStatus() == RentalItemStatus.CANCELLED)
				continue;
			fee = fee.add(i.rentalFee());
			deposit = deposit.add(i.depositTotal());
			damage = damage.add(i.getDamageFee());
		}
		this.rentalFeeTotal = fee.setScale(2, RoundingMode.HALF_UP);
		this.depositTotal = deposit.setScale(2, RoundingMode.HALF_UP);
		this.damageFeeTotal = damage.setScale(2, RoundingMode.HALF_UP);
	}

	/** 대여 중 상태이면서 반납 기한이 지났는지(목록의 overdue 표시) */
	public boolean isOverdue(LocalDateTime now) {
		return status.isOut() && isPastDue(now);
	}

	EquipmentRentalItem item(long itemId) {
		return items.stream().filter(i -> i.getId() != null && i.getId() == itemId).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("이 렌탈의 품목이 아닙니다: " + itemId));
	}

	private boolean hasAnyReturned() {
		return items.stream().anyMatch(i -> i.getReturnedQuantity() > 0);
	}

	private boolean isPastDue(LocalDateTime now) {
		return dueAt != null && dueAt.isBefore(now);
	}

	private void requireStatus(RentalStatus required, String action) {
		if (status != required)
			throw new ConflictException(
					"'" + required + "' 상태의 렌탈만 " + action + "할 수 있습니다. (현재 상태: " + status + ")");
	}

	public Long getId() {
		return id;
	}

	public String getRentalNo() {
		return rentalNo;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public Long getRoundId() {
		return roundId;
	}

	public Long getMemberId() {
		return memberId;
	}

	public String getRenterName() {
		return renterName;
	}

	public String getRenterPhone() {
		return renterPhone;
	}

	public RentalStatus getStatus() {
		return status;
	}

	public LocalDateTime getRentedAt() {
		return rentedAt;
	}

	public LocalDateTime getDueAt() {
		return dueAt;
	}

	public LocalDateTime getReturnedAt() {
		return returnedAt;
	}

	public BigDecimal getRentalFeeTotal() {
		return rentalFeeTotal;
	}

	public BigDecimal getDepositTotal() {
		return depositTotal;
	}

	public BigDecimal getDamageFeeTotal() {
		return damageFeeTotal;
	}

	public Long getReceivedBy() {
		return receivedBy;
	}

	public Long getReturnedTo() {
		return returnedTo;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public List<EquipmentRentalItem> getItems() {
		return Collections.unmodifiableList(items);
	}
}
