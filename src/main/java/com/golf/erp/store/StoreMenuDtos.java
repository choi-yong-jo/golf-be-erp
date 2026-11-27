package com.golf.erp.store;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 매장 메뉴 분류·메뉴 API 요청/응답 */
public final class StoreMenuDtos {

	private StoreMenuDtos() {
	}

	// ---- 메뉴 분류 ----

	/** displayOrder 생략 시 0 */
	public record MenuCategoryRequest(@NotBlank @Size(max = 100) String categoryName,
			@PositiveOrZero @Max(999_999) Integer displayOrder) {
	}

	public record MenuCategoryActiveRequest(@NotNull Boolean active) {
	}

	/** itemCount: 이 분류에 속한 메뉴 수(품절 포함) */
	public record MenuCategoryResponse(Long id, Long storeId, String categoryName, Integer displayOrder,
			boolean active, long itemCount, Long createdBy, Long updatedBy, LocalDateTime createdAt,
			LocalDateTime updatedAt) {

		static MenuCategoryResponse of(StoreMenuCategory c, long itemCount) {
			return new MenuCategoryResponse(c.getId(), c.getStoreId(), c.getCategoryName(), c.getDisplayOrder(),
					c.isActive(), itemCount, c.getCreatedBy(), c.getUpdatedBy(), c.getCreatedAt(), c.getUpdatedAt());
		}
	}

	// ---- 메뉴 ----

	/**
	 * menuCode: 대문자로 저장, 매장 안에서 유일, 이후 변경 불가. salePrice: 0 이상 원 단위.
	 * categoryId·menuImageFileId 선택. available 생략 시 true, displayOrder 생략 시 0.
	 */
	public record MenuItemCreateRequest(
			@NotBlank @Size(max = 40) @Pattern(regexp = "\\s*[A-Za-z0-9_-]+\\s*", message = "메뉴 코드는 영문·숫자·_·- 만 쓸 수 있습니다.") String menuCode,
			Long categoryId, @NotBlank @Size(max = 150) String menuName, @Size(max = 1000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
			Long menuImageFileId, Boolean available, @PositiveOrZero @Max(999_999) Integer displayOrder) {
	}

	/** 메뉴 코드는 바꿀 수 없다. 판매 가능 여부는 availability API 로 바꾼다. */
	public record MenuItemUpdateRequest(Long categoryId, @NotBlank @Size(max = 150) String menuName,
			@Size(max = 1000) String description,
			@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal salePrice,
			Long menuImageFileId, @PositiveOrZero @Max(999_999) Integer displayOrder) {
	}

	/** available=false 품절(판매 중지), true 판매 재개 */
	public record MenuAvailabilityRequest(@NotNull Boolean available) {
	}

	public record MenuItemResponse(Long id, Long storeId, Long categoryId, String categoryName, String menuCode,
			String menuName, String description, BigDecimal salePrice, Long menuImageFileId, boolean available,
			Integer displayOrder, Long createdBy, Long updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {

		static MenuItemResponse of(StoreMenuItem m, String categoryName) {
			return new MenuItemResponse(m.getId(), m.getStoreId(), m.getStoreMenuCategoryId(), categoryName,
					m.getMenuCode(), m.getMenuName(), m.getDescription(), m.getSalePrice(), m.getMenuImageFileId(),
					m.isAvailable(), m.getDisplayOrder(), m.getCreatedBy(), m.getUpdatedBy(), m.getCreatedAt(),
					m.getUpdatedAt());
		}
	}

	// ---- 주문 화면용 메뉴판 ----

	public record MenuBoardItem(Long id, String menuCode, String menuName, String description, BigDecimal salePrice,
			Long menuImageFileId) {

		static MenuBoardItem of(StoreMenuItem m) {
			return new MenuBoardItem(m.getId(), m.getMenuCode(), m.getMenuName(), m.getDescription(),
					m.getSalePrice(), m.getMenuImageFileId());
		}
	}

	/** categoryId·categoryName 이 null 인 묶음은 분류 없는 메뉴(맨 뒤) */
	public record MenuBoardCategory(Long categoryId, String categoryName, List<MenuBoardItem> items) {
	}

	/** orderable: 매장이 영업 중(ACTIVE)이라 지금 주문을 받을 수 있는지 */
	public record MenuBoardResponse(Long storeId, String storeName, StoreStatus storeStatus, boolean orderable,
			List<MenuBoardCategory> categories) {
	}
}
