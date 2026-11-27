package com.golf.erp.rental;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * golf.equipment_rental_item. 렌탈 품목 한 줄. 요금·보증금은 접수 시점의 마스터 값을 복사해 둔다(이후 마스터가 바뀌어도 유지).
 * <p>
 * returned_quantity 는 "처리 끝난 수량"(정상 반납 + 파손 반납 + 분실 확정)이다. 미반납 = quantity - returned_quantity.
 * 파손·분실 수량은 이력(equipment_rental_history 의 DAMAGED·LOST 행)으로 집계한다.
 */
@Entity
@Table(name = "equipment_rental_item")
public class EquipmentRentalItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "equipment_rental_item_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipment_rental_id", nullable = false, updatable = false)
	private EquipmentRental rental;
	@Column(name = "rental_equipment_id", nullable = false, updatable = false)
	private Long rentalEquipmentId;
	@Column(name = "equipment_id")
	private Long equipmentId;
	@Column(name = "quantity", nullable = false)
	private int quantity;
	@Column(name = "returned_quantity", nullable = false)
	private int returnedQuantity;
	@Column(name = "daily_rental_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal dailyRentalFee = BigDecimal.ZERO;
	@Column(name = "rental_days", nullable = false)
	private int rentalDays = 1;
	@Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal depositAmount = BigDecimal.ZERO;
	@Column(name = "damage_fee", nullable = false, precision = 12, scale = 2)
	private BigDecimal damageFee = BigDecimal.ZERO;
	@Enumerated(EnumType.STRING)
	@Column(name = "item_status", nullable = false, length = 20)
	private RentalItemStatus status = RentalItemStatus.RESERVED;
	@Column(name = "condition_out", length = 500)
	private String conditionOut;
	@Column(name = "condition_in", length = 500)
	private String conditionIn;
	@Column(name = "returned_at")
	private LocalDateTime returnedAt;

	protected EquipmentRentalItem() {
	}

	EquipmentRentalItem(EquipmentRental rental, Long rentalEquipmentId, Long equipmentId, int quantity,
			BigDecimal dailyRentalFee, int rentalDays, BigDecimal depositAmount, String conditionOut) {
		if (quantity <= 0)
			throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
		if (rentalDays <= 0)
			throw new IllegalArgumentException("대여 일수는 1 이상이어야 합니다.");
		this.rental = rental;
		this.rentalEquipmentId = rentalEquipmentId;
		this.equipmentId = equipmentId;
		this.quantity = quantity;
		this.dailyRentalFee = money(dailyRentalFee);
		this.rentalDays = rentalDays;
		this.depositAmount = money(depositAmount);
		this.conditionOut = conditionOut;
		this.status = RentalItemStatus.RESERVED;
	}

	/** 출고. 출고 시 개별 자산·상태 메모를 덮어쓸 수 있다(null 이면 유지). */
	void issue(Long equipmentId, String conditionOut) {
		if (status != RentalItemStatus.RESERVED)
			throw new ConflictException("출고 대기 중인 품목이 아닙니다. (품목 " + id + ", 상태: " + status + ")");
		if (equipmentId != null)
			this.equipmentId = equipmentId;
		if (conditionOut != null)
			this.conditionOut = conditionOut;
		this.status = RentalItemStatus.RENTED;
	}

	/**
	 * 반납 처리(부분 가능).
	 *
	 * @param good           정상 반납 수량
	 * @param damaged        파손 반납 수량
	 * @param lost           분실 수량
	 * @param addDamageFee   이번에 더할 파손·분실 변상금
	 * @param damagedBefore  이전 반납에서 파손이 있었는지
	 * @param lostBefore     이전 반납에서 분실이 있었는지
	 */
	void settle(int good, int damaged, int lost, String conditionIn, BigDecimal addDamageFee, boolean damagedBefore,
			boolean lostBefore, LocalDateTime now) {
		if (status != RentalItemStatus.RENTED && status != RentalItemStatus.PARTIALLY_RETURNED)
			throw new ConflictException("반납할 수 있는 품목이 아닙니다. (품목 " + id + ", 상태: " + status + ")");
		if (good < 0 || damaged < 0 || lost < 0)
			throw new IllegalArgumentException("반납·파손·분실 수량은 0 이상이어야 합니다.");
		int processed = good + damaged + lost;
		if (processed == 0)
			throw new IllegalArgumentException("품목 " + id + "의 반납·파손·분실 수량을 하나 이상 입력하세요.");
		if (processed > outstandingQuantity())
			throw new ConflictException("품목 " + id + "의 미반납 수량(" + outstandingQuantity() + "개)보다 많이 처리할 수 없습니다. (요청 "
					+ processed + "개)");
		this.returnedQuantity += processed;
		if (conditionIn != null)
			this.conditionIn = conditionIn;
		if (addDamageFee != null && addDamageFee.signum() > 0)
			this.damageFee = money(damageFee.add(addDamageFee));
		this.returnedAt = now;
		if (outstandingQuantity() > 0)
			this.status = RentalItemStatus.PARTIALLY_RETURNED;
		else if (lostBefore || lost > 0)
			this.status = RentalItemStatus.LOST;
		else if (damagedBefore || damaged > 0)
			this.status = RentalItemStatus.DAMAGED;
		else
			this.status = RentalItemStatus.RETURNED;
	}

	/** 요금 정정. null 인 항목은 그대로 둔다. */
	void changeFees(BigDecimal dailyRentalFee, Integer rentalDays, BigDecimal damageFee) {
		if (status == RentalItemStatus.CANCELLED)
			throw new ConflictException("취소된 품목의 요금은 바꿀 수 없습니다.");
		if (dailyRentalFee != null)
			this.dailyRentalFee = money(dailyRentalFee);
		if (rentalDays != null) {
			if (rentalDays <= 0)
				throw new IllegalArgumentException("대여 일수는 1 이상이어야 합니다.");
			this.rentalDays = rentalDays;
		}
		if (damageFee != null)
			this.damageFee = money(damageFee);
	}

	/** 기한 연장 시 대여 일수 추가 */
	void addRentalDays(int days) {
		this.rentalDays += days;
	}

	void cancel() {
		this.status = RentalItemStatus.CANCELLED;
	}

	public int outstandingQuantity() {
		if (status == RentalItemStatus.CANCELLED)
			return 0;
		return quantity - returnedQuantity;
	}

	/** 일 대여료 × 대여 일수 × 수량 */
	public BigDecimal rentalFee() {
		return dailyRentalFee.multiply(BigDecimal.valueOf((long) rentalDays * quantity)).setScale(2,
				RoundingMode.HALF_UP);
	}

	/** 보증금 × 수량 */
	public BigDecimal depositTotal() {
		return depositAmount.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal money(BigDecimal v) {
		if (v == null || v.signum() < 0)
			throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
		return v.setScale(2, RoundingMode.HALF_UP);
	}

	public Long getId() {
		return id;
	}

	public EquipmentRental getRental() {
		return rental;
	}

	public Long getRentalEquipmentId() {
		return rentalEquipmentId;
	}

	public Long getEquipmentId() {
		return equipmentId;
	}

	public int getQuantity() {
		return quantity;
	}

	public int getReturnedQuantity() {
		return returnedQuantity;
	}

	public BigDecimal getDailyRentalFee() {
		return dailyRentalFee;
	}

	public int getRentalDays() {
		return rentalDays;
	}

	public BigDecimal getDepositAmount() {
		return depositAmount;
	}

	public BigDecimal getDamageFee() {
		return damageFee;
	}

	public RentalItemStatus getStatus() {
		return status;
	}

	public String getConditionOut() {
		return conditionOut;
	}

	public String getConditionIn() {
		return conditionIn;
	}

	public LocalDateTime getReturnedAt() {
		return returnedAt;
	}
}
