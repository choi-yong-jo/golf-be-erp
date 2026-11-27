package com.golf.erp.store;

import com.golf.erp.store.StoreMenuDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 매장 메뉴 분류·메뉴. 권한은 입점 매장(TENANT) 모듈 — /api/tenant-stores/** */
@Tag(name = "매장 메뉴")
@RestController
@RequestMapping("/api/tenant-stores/{storeId}")
public class StoreMenuController {
	private final StoreMenuService menus;

	public StoreMenuController(StoreMenuService menus) {
		this.menus = menus;
	}

	// ---- 메뉴 분류 ----

	@Operation(summary = "메뉴 분류 목록", description = "표시 순서·분류명 순. activeOnly=true 면 사용 중인 분류만. 분류별 메뉴 수 포함")
	@GetMapping("/menu-categories")
	public List<MenuCategoryResponse> categories(@PathVariable long storeId,
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return menus.listCategories(storeId, activeOnly);
	}

	@Operation(summary = "메뉴 분류 등록", description = "분류명은 매장 안에서 유일(409). 폐점 매장 불가(409)")
	@PostMapping("/menu-categories")
	@ResponseStatus(HttpStatus.CREATED)
	public MenuCategoryResponse createCategory(@PathVariable long storeId,
			@Valid @RequestBody MenuCategoryRequest r) {
		return menus.createCategory(storeId, r);
	}

	@Operation(summary = "메뉴 분류 수정", description = "분류명·표시 순서")
	@PutMapping("/menu-categories/{id}")
	public MenuCategoryResponse updateCategory(@PathVariable long storeId, @PathVariable long id,
			@Valid @RequestBody MenuCategoryRequest r) {
		return menus.updateCategory(storeId, id, r);
	}

	@Operation(summary = "메뉴 분류 사용/사용 중지", description = "사용 중지한 분류의 메뉴는 주문 화면 메뉴판에서 빠진다")
	@PatchMapping("/menu-categories/{id}/active")
	public MenuCategoryResponse changeCategoryActive(@PathVariable long storeId, @PathVariable long id,
			@Valid @RequestBody MenuCategoryActiveRequest r) {
		return menus.changeCategoryActive(storeId, id, r.active());
	}

	// ---- 메뉴 ----

	@Operation(summary = "메뉴 목록", description = "분류 표시 순서 → 메뉴 표시 순서 순(분류 없는 메뉴는 맨 뒤). "
			+ "keyword: 메뉴 코드·메뉴명, available: 판매 가능 여부")
	@GetMapping("/menu-items")
	public List<MenuItemResponse> items(@PathVariable long storeId, @RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Boolean available, @RequestParam(required = false) String keyword) {
		return menus.listItems(storeId, categoryId, available, keyword);
	}

	@Operation(summary = "메뉴 상세")
	@GetMapping("/menu-items/{id}")
	public MenuItemResponse item(@PathVariable long storeId, @PathVariable long id) {
		return menus.getItem(storeId, id);
	}

	@Operation(summary = "메뉴 등록", description = "menuCode 는 대문자로 저장, 매장 안에서 유일(409). 판매가는 0 이상 원 단위. "
			+ "분류는 같은 매장의 사용 중인 분류, 이미지는 ACTIVE 파일")
	@PostMapping("/menu-items")
	@ResponseStatus(HttpStatus.CREATED)
	public MenuItemResponse createItem(@PathVariable long storeId, @Valid @RequestBody MenuItemCreateRequest r) {
		return menus.createItem(storeId, r);
	}

	@Operation(summary = "메뉴 수정", description = "메뉴 코드는 바꿀 수 없다. 판매가 변경은 감사 로그에 남고, 이미 접수된 주문 금액은 그대로")
	@PutMapping("/menu-items/{id}")
	public MenuItemResponse updateItem(@PathVariable long storeId, @PathVariable long id,
			@Valid @RequestBody MenuItemUpdateRequest r) {
		return menus.updateItem(storeId, id, r);
	}

	@Operation(summary = "메뉴 판매 가능/품절", description = "available=false 품절(판매 중지). 메뉴는 주문이 참조하므로 삭제하지 않는다")
	@PatchMapping("/menu-items/{id}/availability")
	public MenuItemResponse changeAvailability(@PathVariable long storeId, @PathVariable long id,
			@Valid @RequestBody MenuAvailabilityRequest r) {
		return menus.changeAvailability(storeId, id, r.available());
	}

	@Operation(summary = "주문 화면 메뉴판", description = "사용 중인 분류별 판매 가능 메뉴. 분류 없는 메뉴는 맨 뒤 묶음(categoryId=null)")
	@GetMapping("/menu")
	public MenuBoardResponse menuBoard(@PathVariable long storeId) {
		return menus.menuBoard(storeId);
	}
}
