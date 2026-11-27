package com.golf.erp.store;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * golf.store_menu_item. 매장 판매 메뉴. 메뉴 코드는 매장 안에서 유일(uq_store_menu_item)하고 대문자로 저장하며 바꾸지 않는다.
 * 주문 항목이 참조하므로 물리 삭제하지 않는다 — 판매 중지는 is_available=false(품절).
 * 판매가는 0 이상 원 단위(ck_store_menu_price), display_order ≥ 0 (ck_store_menu_order).
 */
@Entity
@Table(name = "store_menu_item")
public class StoreMenuItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_menu_item_id")
	private Long id;
	@Column(name = "store_id", nullable = false, updatable = false)
	private Long storeId;
	@Column(name = "store_menu_category_id")
	private Long storeMenuCategoryId;
	@Column(name = "menu_code", nullable = false, length = 40, updatable = false)
	private String menuCode;
	@Column(name = "menu_name", nullable = false, length = 150)
	private String menuName;
	@Column(name = "description", length = 1000)
	private String description;
	@Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal salePrice;
	@Column(name = "menu_image_file_id")
	private Long menuImageFileId;
	@Column(name = "is_available", nullable = false)
	private boolean available = true;
	@Column(name = "display_order", nullable = false)
	private Integer displayOrder = 0;
	@Column(name = "created_by", updatable = false)
	private Long createdBy;
	@Column(name = "updated_by")
	private Long updatedBy;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected StoreMenuItem() {
	}

	public StoreMenuItem(Long storeId, String menuCode, Long actorId, LocalDateTime now) {
		this.storeId = storeId;
		this.menuCode = menuCode;
		this.createdBy = actorId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 메뉴 코드·매장을 뺀 전체 수정. 분류·이미지 파일 확인은 서비스가 먼저 한다. */
	public void update(Long storeMenuCategoryId, String menuName, String description, BigDecimal salePrice,
			Long menuImageFileId, int displayOrder, Long actorId, LocalDateTime now) {
		if (salePrice == null || salePrice.signum() < 0)
			throw new IllegalArgumentException("판매가는 0 이상이어야 합니다.");
		if (!StoreOrderItem.isWhole(salePrice))
			throw new IllegalArgumentException("판매가는 원 단위(소수점 없음)로 입력하세요.");
		if (displayOrder < 0)
			throw new IllegalArgumentException("표시 순서는 0 이상이어야 합니다.");
		this.storeMenuCategoryId = storeMenuCategoryId;
		this.menuName = menuName;
		this.description = description;
		this.salePrice = salePrice.setScale(2, RoundingMode.HALF_UP);
		this.menuImageFileId = menuImageFileId;
		this.displayOrder = displayOrder;
		touch(actorId, now);
	}

	/** 판매 가능/품절(판매 중지) 전환 */
	public void changeAvailable(boolean available, Long actorId, LocalDateTime now) {
		this.available = available;
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

	public Long getStoreMenuCategoryId() {
		return storeMenuCategoryId;
	}

	public String getMenuCode() {
		return menuCode;
	}

	public String getMenuName() {
		return menuName;
	}

	public String getDescription() {
		return description;
	}

	public BigDecimal getSalePrice() {
		return salePrice;
	}

	public Long getMenuImageFileId() {
		return menuImageFileId;
	}

	public boolean isAvailable() {
		return available;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
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
