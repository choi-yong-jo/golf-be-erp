package com.golf.erp.rental;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.rental_equipment_stock. 골프장별 품목 수량. DB CHECK: total = available + rented + maintenance, 모두 0 이상.
 * 수량을 바꿀 때는 반드시 {@code RentalEquipmentStockRepository.findForUpdate} 로 잠근 뒤 {@link #apply} 를 호출한다.
 */
@Entity
@Table(name = "rental_equipment_stock")
public class RentalEquipmentStock {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "rental_equipment_stock_id")
	private Long id;
	@Column(name = "rental_equipment_id", nullable = false, updatable = false)
	private Long rentalEquipmentId;
	@Column(name = "golf_course_id", nullable = false, updatable = false)
	private Long golfCourseId;
	@Column(name = "total_quantity", nullable = false)
	private int totalQuantity;
	@Column(name = "available_quantity", nullable = false)
	private int availableQuantity;
	@Column(name = "rented_quantity", nullable = false)
	private int rentedQuantity;
	@Column(name = "maintenance_quantity", nullable = false)
	private int maintenanceQuantity;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected RentalEquipmentStock() {
	}

	/**
	 * 수량 이동. 결과가 음수가 되는 칸이 있으면 아무것도 바꾸지 않고 409.
	 */
	public void apply(StockMovement m, int quantity, LocalDateTime now) {
		if (quantity <= 0)
			throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
		int available = availableQuantity + m.available * quantity;
		int rented = rentedQuantity + m.rented * quantity;
		int maintenance = maintenanceQuantity + m.maintenance * quantity;
		if (available < 0)
			throw new ConflictException(shortage("가용", availableQuantity, quantity));
		if (rented < 0)
			throw new ConflictException(shortage("대여 중", rentedQuantity, quantity));
		if (maintenance < 0)
			throw new ConflictException(shortage("정비 중", maintenanceQuantity, quantity));
		this.availableQuantity = available;
		this.rentedQuantity = rented;
		this.maintenanceQuantity = maintenance;
		this.totalQuantity = totalQuantity + m.total * quantity;
		this.updatedAt = now;
	}

	/**
	 * 실사 조정: 가용 수량을 target 으로 맞추고 차이만큼 총 수량을 늘리거나 줄인다(대여 중·정비 중은 그대로).
	 *
	 * @return 가용 수량 변화량(+ 증가, - 감소)
	 */
	public int adjustAvailableTo(int target, LocalDateTime now) {
		if (target < 0)
			throw new IllegalArgumentException("조정 후 가용 수량은 0 이상이어야 합니다.");
		int delta = target - availableQuantity;
		if (delta == 0)
			throw new IllegalArgumentException("현재 가용 수량(" + availableQuantity + "개)과 같아 조정할 것이 없습니다.");
		this.availableQuantity = target;
		this.totalQuantity = totalQuantity + delta;
		this.updatedAt = now;
		return delta;
	}

	private static String shortage(String what, int have, int requested) {
		return what + " 수량이 부족합니다. (현재 " + what + " " + have + "개, 요청 " + requested + "개)";
	}

	public Long getId() {
		return id;
	}

	public Long getRentalEquipmentId() {
		return rentalEquipmentId;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public int getTotalQuantity() {
		return totalQuantity;
	}

	public int getAvailableQuantity() {
		return availableQuantity;
	}

	public int getRentedQuantity() {
		return rentedQuantity;
	}

	public int getMaintenanceQuantity() {
		return maintenanceQuantity;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
