package com.golf.erp.store;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.security.CurrentUser;
import com.golf.erp.store.StoreDtos.StoreBrandCreateRequest;
import com.golf.erp.store.StoreDtos.StoreBrandResponse;
import com.golf.erp.store.StoreDtos.StoreBrandUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class StoreBrandService {
	private final StoreBrandRepository repo;
	private final TenantStoreRepository stores;
	private final AuditLogger audit;
	private final Clock clock;

	public StoreBrandService(StoreBrandRepository repo, TenantStoreRepository stores, AuditLogger audit,
			Clock clock) {
		this.repo = repo;
		this.stores = stores;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 브랜드 코드·브랜드명·사업자명 */
	@Transactional(readOnly = true)
	public PageResult<StoreBrandResponse> list(Boolean active, String keyword, int page, int size) {
		Specification<StoreBrand> spec = Specification.allOf(Specs.eq("active", active),
				Specs.like(keyword, "brandCode", "brandName", "businessName"));
		Page<StoreBrand> result = repo.findAll(spec, Pages.of(page, size, Sort.by("brandName", "brandCode")));
		Map<Long, Map<StoreStatus, Long>> counts = storeCounts(result.getContent().stream().map(StoreBrand::getId)
				.toList());
		return PageResult.of(result, b -> StoreBrandResponse.of(b, counts.getOrDefault(b.getId(), emptyCounts())));
	}

	@Transactional(readOnly = true)
	public StoreBrandResponse get(long id) {
		return toResponse(find(id));
	}

	public StoreBrandResponse create(StoreBrandCreateRequest r) {
		String code = r.brandCode().trim().toUpperCase(Locale.ROOT);
		if (repo.existsByBrandCode(code))
			throw new ConflictException("이미 있는 브랜드 코드입니다: " + code);
		LocalDateTime now = now();
		Long actor = CurrentUser.idOrNull();
		StoreBrand b = new StoreBrand(code, actor, now);
		b.update(r.brandName().trim(), blankToNull(r.businessName()), blankToNull(r.businessRegistrationNo()),
				blankToNull(r.contactName()), blankToNull(r.contactPhone()), blankToNull(r.contactEmail()), actor,
				now);
		return StoreBrandResponse.of(repo.save(b), emptyCounts());
	}

	public StoreBrandResponse update(long id, StoreBrandUpdateRequest r) {
		StoreBrand b = find(id);
		b.update(r.brandName().trim(), blankToNull(r.businessName()), blankToNull(r.businessRegistrationNo()),
				blankToNull(r.contactName()), blankToNull(r.contactPhone()), blankToNull(r.contactEmail()),
				CurrentUser.idOrNull(), now());
		return toResponse(b);
	}

	/** 영업 중(ACTIVE) 매장이 있는 브랜드는 사용 중지할 수 없다(먼저 매장을 휴점·정지·폐점). */
	public StoreBrandResponse changeActive(long id, boolean active) {
		StoreBrand b = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("입점 브랜드", id));
		if (b.isActive() == active)
			return toResponse(b);
		if (!active) {
			long activeStores = stores.countByStoreBrandIdAndStatus(id, StoreStatus.ACTIVE);
			if (activeStores > 0)
				throw new ConflictException(
						"영업 중인 매장이 " + activeStores + "곳 있는 브랜드는 사용 중지할 수 없습니다. 매장 상태를 먼저 바꾸세요.");
		}
		b.changeActive(active, CurrentUser.idOrNull(), now());
		audit.log(active ? "ACTIVATE" : "DEACTIVATE", "store_brand", id, Map.of("brandCode", b.getBrandCode()));
		return toResponse(b);
	}

	private StoreBrandResponse toResponse(StoreBrand b) {
		return StoreBrandResponse.of(b, storeCounts(List.of(b.getId())).getOrDefault(b.getId(), emptyCounts()));
	}

	/** 브랜드 id → (상태 → 매장 수). 모든 상태를 0 으로 채운다. */
	private Map<Long, Map<StoreStatus, Long>> storeCounts(Collection<Long> brandIds) {
		Map<Long, Map<StoreStatus, Long>> m = new HashMap<>();
		if (brandIds.isEmpty())
			return m;
		for (Object[] row : stores.countByBrandAndStatus(brandIds))
			m.computeIfAbsent((Long) row[0], k -> emptyCounts()).put((StoreStatus) row[1], (Long) row[2]);
		return m;
	}

	private static Map<StoreStatus, Long> emptyCounts() {
		Map<StoreStatus, Long> m = new EnumMap<>(StoreStatus.class);
		for (StoreStatus s : StoreStatus.values())
			m.put(s, 0L);
		return m;
	}

	private StoreBrand find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("입점 브랜드", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
