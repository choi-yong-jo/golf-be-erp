package com.golf.erp.store;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Ref;
import com.golf.erp.common.Specs;
import com.golf.erp.security.CurrentUser;
import com.golf.erp.store.StoreMenuDtos.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매장 메뉴 분류·메뉴 관리. 폐점(CLOSED) 매장의 메뉴는 바꿀 수 없다.
 * 메뉴는 주문 항목이 참조하므로 삭제하지 않고 품절(is_available=false)로 판매를 멈춘다.
 */
@Service
@Transactional
public class StoreMenuService {
	private final StoreMenuCategoryRepository categories;
	private final StoreMenuItemRepository items;
	private final TenantStoreRepository stores;
	private final JdbcTemplate jdbc;
	private final AuditLogger audit;
	private final Clock clock;

	public StoreMenuService(StoreMenuCategoryRepository categories, StoreMenuItemRepository items,
			TenantStoreRepository stores, JdbcTemplate jdbc, AuditLogger audit, Clock clock) {
		this.categories = categories;
		this.items = items;
		this.stores = stores;
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	// ---- 메뉴 분류 ----

	/** 표시 순서·분류명 순. activeOnly=true 면 사용 중인 분류만 */
	@Transactional(readOnly = true)
	public List<MenuCategoryResponse> listCategories(long storeId, boolean activeOnly) {
		findStore(storeId);
		Map<Long, Long> counts = itemCounts(storeId);
		return categories.findByStoreId(storeId).stream().filter(c -> !activeOnly || c.isActive())
				.sorted(CATEGORY_ORDER).map(c -> MenuCategoryResponse.of(c, counts.getOrDefault(c.getId(), 0L)))
				.toList();
	}

	/** 분류명은 매장 안에서 유일(409) */
	public MenuCategoryResponse createCategory(long storeId, MenuCategoryRequest r) {
		requireEditableStore(storeId);
		String name = r.categoryName().trim();
		if (categories.existsByStoreIdAndCategoryName(storeId, name))
			throw new ConflictException("이 매장에 이미 있는 메뉴 분류입니다: " + name);
		LocalDateTime now = now();
		Long actor = CurrentUser.idOrNull();
		StoreMenuCategory c = new StoreMenuCategory(storeId, actor, now);
		c.update(name, orZero(r.displayOrder()), actor, now);
		return MenuCategoryResponse.of(categories.save(c), 0L);
	}

	public MenuCategoryResponse updateCategory(long storeId, long id, MenuCategoryRequest r) {
		requireEditableStore(storeId);
		StoreMenuCategory c = findCategory(storeId, id);
		String name = r.categoryName().trim();
		if (categories.existsByStoreIdAndCategoryNameAndIdNot(storeId, name, id))
			throw new ConflictException("이 매장에 이미 있는 메뉴 분류입니다: " + name);
		c.update(name, orZero(r.displayOrder()), CurrentUser.idOrNull(), now());
		return MenuCategoryResponse.of(c, itemCounts(storeId).getOrDefault(id, 0L));
	}

	/** 사용 중지한 분류의 메뉴는 주문 화면 메뉴판에서 빠진다(메뉴 자체는 그대로). */
	public MenuCategoryResponse changeCategoryActive(long storeId, long id, boolean active) {
		requireEditableStore(storeId);
		StoreMenuCategory c = findCategory(storeId, id);
		if (c.isActive() != active)
			c.changeActive(active, CurrentUser.idOrNull(), now());
		return MenuCategoryResponse.of(c, itemCounts(storeId).getOrDefault(id, 0L));
	}

	// ---- 메뉴 ----

	/** 분류 표시 순서 → 메뉴 표시 순서 → 메뉴명 순(분류 없는 메뉴는 맨 뒤). keyword: 메뉴 코드·메뉴명 */
	@Transactional(readOnly = true)
	public List<MenuItemResponse> listItems(long storeId, Long categoryId, Boolean available, String keyword) {
		findStore(storeId);
		Map<Long, StoreMenuCategory> cats = categoryMap(storeId);
		Specification<StoreMenuItem> spec = Specification.allOf(Specs.eq("storeId", storeId),
				Specs.eq("storeMenuCategoryId", categoryId), Specs.eq("available", available),
				Specs.like(keyword, "menuCode", "menuName"));
		return items.findAll(spec, Sort.by("id")).stream().sorted(itemOrder(cats))
				.map(m -> MenuItemResponse.of(m, categoryName(cats, m))).toList();
	}

	@Transactional(readOnly = true)
	public MenuItemResponse getItem(long storeId, long id) {
		findStore(storeId);
		return toResponse(findItem(storeId, id));
	}

	/** 메뉴 코드는 대문자로 저장, 매장 안에서 유일(409). 분류는 같은 매장의 사용 중인 분류, 이미지는 ACTIVE 파일이어야 한다. */
	public MenuItemResponse createItem(long storeId, MenuItemCreateRequest r) {
		requireEditableStore(storeId);
		String code = r.menuCode().trim().toUpperCase(Locale.ROOT);
		if (items.existsByStoreIdAndMenuCode(storeId, code))
			throw new ConflictException("이 매장에 이미 있는 메뉴 코드입니다: " + code);
		requireCategory(storeId, r.categoryId(), null);
		requireImageFile(r.menuImageFileId(), null);
		LocalDateTime now = now();
		Long actor = CurrentUser.idOrNull();
		StoreMenuItem m = new StoreMenuItem(storeId, code, actor, now);
		m.update(r.categoryId(), r.menuName().trim(), blankToNull(r.description()), r.salePrice(),
				r.menuImageFileId(), orZero(r.displayOrder()), actor, now);
		if (r.available() != null && !r.available())
			m.changeAvailable(false, actor, now);
		return toResponse(items.save(m));
	}

	/** 메뉴 코드를 뺀 전체 수정. 판매가가 바뀌면 감사 로그(PRICE_CHANGE). 이미 접수된 주문 금액은 바뀌지 않는다. */
	public MenuItemResponse updateItem(long storeId, long id, MenuItemUpdateRequest r) {
		requireEditableStore(storeId);
		StoreMenuItem m = findItem(storeId, id);
		requireCategory(storeId, r.categoryId(), m.getStoreMenuCategoryId());
		requireImageFile(r.menuImageFileId(), m.getMenuImageFileId());
		BigDecimal before = m.getSalePrice();
		m.update(r.categoryId(), r.menuName().trim(), blankToNull(r.description()), r.salePrice(),
				r.menuImageFileId(), orZero(r.displayOrder()), CurrentUser.idOrNull(), now());
		if (before.compareTo(m.getSalePrice()) != 0) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("storeId", storeId);
			details.put("menuCode", m.getMenuCode());
			details.put("before", before);
			details.put("after", m.getSalePrice());
			audit.log("PRICE_CHANGE", "store_menu_item", id, details);
		}
		return toResponse(m);
	}

	/** 판매 가능/품절 전환. 물리 삭제 대신 이것으로 판매를 멈춘다. */
	public MenuItemResponse changeAvailability(long storeId, long id, boolean available) {
		requireEditableStore(storeId);
		StoreMenuItem m = findItem(storeId, id);
		if (m.isAvailable() != available)
			m.changeAvailable(available, CurrentUser.idOrNull(), now());
		return toResponse(m);
	}

	// ---- 주문 화면용 메뉴판 ----

	/** 사용 중인 분류별 판매 가능 메뉴. 분류 없는 메뉴는 맨 뒤 묶음(categoryId=null), 사용 중지 분류의 메뉴는 빠진다. */
	@Transactional(readOnly = true)
	public MenuBoardResponse menuBoard(long storeId) {
		TenantStore s = findStore(storeId);
		Map<Long, StoreMenuCategory> cats = categoryMap(storeId);
		Map<Long, List<MenuBoardItem>> byCategory = new LinkedHashMap<>();
		List<MenuBoardItem> uncategorized = new ArrayList<>();
		items.findByStoreIdAndAvailableTrue(storeId).stream().sorted(itemOrder(cats)).forEach(m -> {
			Long cid = m.getStoreMenuCategoryId();
			if (cid == null)
				uncategorized.add(MenuBoardItem.of(m));
			else if (cats.containsKey(cid) && cats.get(cid).isActive())
				byCategory.computeIfAbsent(cid, k -> new ArrayList<>()).add(MenuBoardItem.of(m));
		});
		List<MenuBoardCategory> groups = new ArrayList<>();
		cats.values().stream().filter(StoreMenuCategory::isActive).sorted(CATEGORY_ORDER)
				.filter(c -> byCategory.containsKey(c.getId()))
				.forEach(c -> groups.add(new MenuBoardCategory(c.getId(), c.getCategoryName(),
						byCategory.get(c.getId()))));
		if (!uncategorized.isEmpty())
			groups.add(new MenuBoardCategory(null, null, uncategorized));
		return new MenuBoardResponse(s.getId(), s.getStoreName(), s.getStatus(),
				s.getStatus() == StoreStatus.ACTIVE, groups);
	}

	// ---- 내부 ----

	private static final Comparator<StoreMenuCategory> CATEGORY_ORDER = Comparator
			.comparing(StoreMenuCategory::getDisplayOrder).thenComparing(StoreMenuCategory::getCategoryName)
			.thenComparing(StoreMenuCategory::getId);

	/** 분류 표시 순서(분류 없음은 맨 뒤) → 분류명 → 메뉴 표시 순서 → 메뉴명 → id */
	private static Comparator<StoreMenuItem> itemOrder(Map<Long, StoreMenuCategory> cats) {
		Function<StoreMenuItem, StoreMenuCategory> cat = m -> m.getStoreMenuCategoryId() == null ? null
				: cats.get(m.getStoreMenuCategoryId());
		return Comparator.comparing(cat, Comparator.nullsLast(CATEGORY_ORDER))
				.thenComparing(StoreMenuItem::getDisplayOrder).thenComparing(StoreMenuItem::getMenuName)
				.thenComparing(StoreMenuItem::getId);
	}

	private MenuItemResponse toResponse(StoreMenuItem m) {
		String name = m.getStoreMenuCategoryId() == null ? null
				: categories.findById(m.getStoreMenuCategoryId()).map(StoreMenuCategory::getCategoryName)
						.orElse(null);
		return MenuItemResponse.of(m, name);
	}

	private static String categoryName(Map<Long, StoreMenuCategory> cats, StoreMenuItem m) {
		StoreMenuCategory c = m.getStoreMenuCategoryId() == null ? null : cats.get(m.getStoreMenuCategoryId());
		return c == null ? null : c.getCategoryName();
	}

	private Map<Long, StoreMenuCategory> categoryMap(long storeId) {
		return categories.findByStoreId(storeId).stream()
				.collect(Collectors.toMap(StoreMenuCategory::getId, Function.identity()));
	}

	private Map<Long, Long> itemCounts(long storeId) {
		Map<Long, Long> m = new HashMap<>();
		for (Object[] row : items.countByCategory(storeId))
			m.put((Long) row[0], (Long) row[1]);
		return m;
	}

	/**
	 * 분류는 같은 매장의 것이어야 한다(다른 매장 분류 id → 404). 새로 지정하는 분류는 사용 중이어야 한다
	 * (이미 지정돼 있던 분류는 사용 중지 상태여도 그대로 둘 수 있다).
	 */
	private void requireCategory(long storeId, Long categoryId, Long currentCategoryId) {
		if (categoryId == null)
			return;
		StoreMenuCategory c = findCategory(storeId, categoryId);
		if (!c.isActive() && !Objects.equals(categoryId, currentCategoryId))
			throw new ConflictException("사용 중지된 메뉴 분류에는 메뉴를 넣을 수 없습니다: " + c.getCategoryName());
	}

	/**
	 * 메뉴 이미지는 golf.common_file 의 ACTIVE 파일이어야 한다(없으면 404, 격리·삭제된 파일이면 400).
	 * 처리 중 파일이 삭제되지 않도록 FOR SHARE 로 잠근다. 바뀌지 않은 기존 파일은 다시 확인하지 않는다.
	 */
	private void requireImageFile(Long fileId, Long currentFileId) {
		if (fileId == null || fileId.equals(currentFileId))
			return;
		List<String> status = jdbc.queryForList(
				"SELECT upload_status FROM golf.common_file WHERE common_file_id = ? FOR SHARE", String.class, fileId);
		if (status.isEmpty())
			throw NotFoundException.of(Ref.COMMON_FILE.label, fileId);
		if (!"ACTIVE".equals(status.get(0)))
			throw new IllegalArgumentException("사용할 수 없는 파일입니다(상태: " + status.get(0) + "): " + fileId);
	}

	private StoreMenuCategory findCategory(long storeId, long id) {
		return categories.findByIdAndStoreId(id, storeId).orElseThrow(() -> NotFoundException.of("메뉴 분류", id));
	}

	private StoreMenuItem findItem(long storeId, long id) {
		return items.findByIdAndStoreId(id, storeId)
				.orElseThrow(() -> NotFoundException.of(Ref.STORE_MENU_ITEM.label, id));
	}

	private TenantStore findStore(long storeId) {
		return stores.findById(storeId).orElseThrow(() -> NotFoundException.of(Ref.TENANT_STORE.label, storeId));
	}

	/** 폐점(CLOSED) 매장은 메뉴를 바꿀 수 없다. */
	private TenantStore requireEditableStore(long storeId) {
		TenantStore s = findStore(storeId);
		if (s.getStatus() == StoreStatus.CLOSED)
			throw new ConflictException("폐점한 매장의 메뉴는 바꿀 수 없습니다: " + s.getStoreCode());
		return s;
	}

	private static int orZero(Integer v) {
		return v == null ? 0 : v;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
