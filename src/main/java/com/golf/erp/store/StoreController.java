package com.golf.erp.store;

import com.golf.erp.common.PageResult;
import com.golf.erp.store.StoreDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "입점 매장")
@RestController
@RequestMapping("/api")
public class StoreController {
	private final StoreBrandService brands;
	private final TenantStoreService stores;

	public StoreController(StoreBrandService brands, TenantStoreService stores) {
		this.brands = brands;
		this.stores = stores;
	}

	// ---- 입점 브랜드 ----

	@Operation(summary = "입점 브랜드 목록", description = "keyword: 브랜드 코드·브랜드명·사업자명. 상태별 매장 수 포함")
	@GetMapping("/store-brands")
	public PageResult<StoreBrandResponse> brands(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return brands.list(active, keyword, page, size);
	}

	@Operation(summary = "입점 브랜드 상세", description = "상태별 매장 수(storeCounts) 포함")
	@GetMapping("/store-brands/{id}")
	public StoreBrandResponse brand(@PathVariable long id) {
		return brands.get(id);
	}

	@Operation(summary = "입점 브랜드 등록", description = "brandCode 는 대문자로 저장, 중복이면 409")
	@PostMapping("/store-brands")
	@ResponseStatus(HttpStatus.CREATED)
	public StoreBrandResponse createBrand(@Valid @RequestBody StoreBrandCreateRequest r) {
		return brands.create(r);
	}

	@Operation(summary = "입점 브랜드 수정", description = "브랜드 코드는 바꿀 수 없다")
	@PutMapping("/store-brands/{id}")
	public StoreBrandResponse updateBrand(@PathVariable long id, @Valid @RequestBody StoreBrandUpdateRequest r) {
		return brands.update(id, r);
	}

	@Operation(summary = "입점 브랜드 사용/사용 중지", description = "영업 중(ACTIVE) 매장이 있으면 사용 중지 불가(409)")
	@PatchMapping("/store-brands/{id}/active")
	public StoreBrandResponse changeBrandActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
		return brands.changeActive(id, r.active());
	}

	@Operation(summary = "입점 브랜드 사용 중지", description = "물리 삭제하지 않고 is_active=false 로 바꾼다")
	@DeleteMapping("/store-brands/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivateBrand(@PathVariable long id) {
		brands.changeActive(id, false);
	}

	// ---- 입점 매장 ----

	@Operation(summary = "입점 매장 목록", description = "keyword: 매장 코드·매장명·담당자명. "
			+ "leaseExpiringWithinDays=N: 임대 종료일이 오늘~오늘+N 인 매장")
	@GetMapping("/tenant-stores")
	public PageResult<TenantStoreResponse> stores(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) StoreType storeType, @RequestParam(required = false) StoreStatus status,
			@RequestParam(required = false) Long storeBrandId, @RequestParam(required = false) Boolean externalTenant,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer leaseExpiringWithinDays,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return stores.list(golfCourseId, storeType, status, storeBrandId, externalTenant, keyword,
				leaseExpiringWithinDays, page, size);
	}

	@Operation(summary = "임대 만료 예정 매장", description = "임대 종료일이 오늘~오늘+days 인 매장(폐점 제외), 종료일 빠른 순")
	@GetMapping("/tenant-stores/lease-expiring")
	public List<TenantStoreResponse> leaseExpiring(@RequestParam(defaultValue = "30") int days,
			@RequestParam(required = false) Long golfCourseId) {
		return stores.leaseExpiring(days, golfCourseId);
	}

	@Operation(summary = "입점 매장 상세", description = "골프장 이름·브랜드 이름 포함")
	@GetMapping("/tenant-stores/{id}")
	public TenantStoreResponse store(@PathVariable long id) {
		return stores.get(id);
	}

	@Operation(summary = "입점 매장 등록", description = "매장 코드는 골프장 안에서 유일(409). 브랜드는 사용 중이어야 한다")
	@PostMapping("/tenant-stores")
	@ResponseStatus(HttpStatus.CREATED)
	public TenantStoreResponse createStore(@Valid @RequestBody TenantStoreCreateRequest r) {
		return stores.create(r);
	}

	@Operation(summary = "입점 매장 수정", description = "임대 정보(기간·월 임대료) 포함. 폐점 매장은 수정 불가(409)")
	@PutMapping("/tenant-stores/{id}")
	public TenantStoreResponse updateStore(@PathVariable long id, @Valid @RequestBody TenantStoreUpdateRequest r) {
		return stores.update(id, r);
	}

	@Operation(summary = "입점 매장 상태 변경", description = "ACTIVE, INACTIVE, SUSPENDED, CLOSED. CLOSED 는 최종 상태(이후 변경 409)")
	@PatchMapping("/tenant-stores/{id}/status")
	public TenantStoreResponse changeStoreStatus(@PathVariable long id, @Valid @RequestBody StoreStatusRequest r) {
		return stores.changeStatus(id, r.status(), r.reason());
	}
}
