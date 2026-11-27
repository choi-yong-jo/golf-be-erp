package com.golf.erp.store;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.store_menu_category. 매장 메뉴 분류. 분류명은 매장 안에서 유일(uq_store_menu_category),
 * display_order ≥ 0 (ck_store_menu_category_order). 매장은 바꿀 수 없다.
 */
@Entity
@Table(name = "store_menu_category")
public class StoreMenuCategory {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_menu_category_id")
	private Long id;
	@Column(name = "store_id", nullable = false, updatable = false)
	private Long storeId;
	@Column(name = "category_name", nullable = false, length = 100)
	private String categoryName;
	@Column(name = "display_order", nullable = false)
	private Integer displayOrder = 0;
	@Column(name = "is_active", nullable = false)
	private boolean active = true;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected StoreMenuCategory() {
	}

	public StoreMenuCategory(Long storeId, Long actorId, LocalDateTime now) {
		this.storeId = storeId;
		this.createdBy = actorId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String categoryName, int displayOrder, Long actorId, LocalDateTime now) {
		if (displayOrder < 0)
			throw new IllegalArgumentException("표시 순서는 0 이상이어야 합니다.");
		this.categoryName = categoryName;
		this.displayOrder = displayOrder;
		touch(actorId, now);
	}

	public void changeActive(boolean active, Long actorId, LocalDateTime now) {
		this.active = active;
		touch(actorId, now);
	}

	private void touch(Long actorId, LocalDateTime now) {
		this.updatedBy = actorId;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getStoreId() {
		return storeId;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public boolean isActive() {
		return active;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getUpdatedBy() {
		return updatedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
