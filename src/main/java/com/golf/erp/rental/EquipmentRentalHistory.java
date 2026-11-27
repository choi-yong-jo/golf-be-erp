package com.golf.erp.rental;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** golf.equipment_rental_history. 렌탈 처리 이력(추가만). 품목 단위 처리면 equipment_rental_item_id·quantity 를 채운다. */
@Entity
@Table(name = "equipment_rental_history")
public class EquipmentRentalHistory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "rental_history_id")
	private Long id;
	@Column(name = "equipment_rental_id", nullable = false, updatable = false)
	private Long equipmentRentalId;
	@Column(name = "equipment_rental_item_id", updatable = false)
	private Long equipmentRentalItemId;
	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false, length = 30, updatable = false)
	private RentalAction action;
	@Column(name = "quantity", updatable = false)
	private Integer quantity;
	@Column(name = "condition_note", length = 1000, updatable = false)
	private String conditionNote;
	@Column(name = "actor_admin_user_id", updatable = false)
	private Long actorAdminUserId;
	@Column(name = "occurred_at", nullable = false, updatable = false)
	private LocalDateTime occurredAt;

	protected EquipmentRentalHistory() {
	}

	public EquipmentRentalHistory(Long equipmentRentalId, Long equipmentRentalItemId, RentalAction action,
			Integer quantity, String conditionNote, Long actorAdminUserId, LocalDateTime occurredAt) {
		this.equipmentRentalId = equipmentRentalId;
		this.equipmentRentalItemId = equipmentRentalItemId;
		this.action = action;
		this.quantity = quantity;
		this.conditionNote = conditionNote != null && conditionNote.length() > 1000 ? conditionNote.substring(0, 1000)
				: conditionNote;
		this.actorAdminUserId = actorAdminUserId;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public Long getEquipmentRentalId() {
		return equipmentRentalId;
	}

	public Long getEquipmentRentalItemId() {
		return equipmentRentalItemId;
	}

	public RentalAction getAction() {
		return action;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public String getConditionNote() {
		return conditionNote;
	}

	public Long getActorAdminUserId() {
		return actorAdminUserId;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}
}
