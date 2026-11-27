package com.golf.erp.store;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.security.CurrentUser;
import com.golf.erp.store.StoreDtos.TenantStoreCreateRequest;
import com.golf.erp.store.StoreDtos.TenantStoreResponse;
import com.golf.erp.store.StoreDtos.TenantStoreUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class TenantStoreService {
	/** 임대 만료 조회 기간 상한(일) */
	private static final int MAX_LEASE_DAYS = 3650;

	private final TenantStoreRepository repo;
	private final StoreBrandRepository brands;
	private final GolfCourseNameLookup golfCourses;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public TenantStoreService(TenantStoreRepository repo, StoreBrandRepository brands,
			GolfCourseNameLookup golfCourses, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.brands = brands;
		this.golfCourses = golfCourses;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * @param keyword                 매장 코드·매장명·담당자명
	 * @param leaseExpiringWithinDays N 이면 임대 종료일이 오늘 ~ 오늘+N 인 매장만
	 */
	@Transactional(readOnly = true)
	public PageResult<TenantStoreResponse> list(Long golfCourseId, StoreType storeType, StoreStatus status,
			Long storeBrandId, Boolean externalTenant, String keyword, Integer leaseExpiringWithinDays, int page,
			int size) {
		LocalDate today = today();
		Specification<TenantStore> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("storeType", storeType), Specs.eq("status", status), Specs.eq("storeBrandId", storeBrandId),
				Specs.eq("externalTenant", externalTenant),
				Specs.like(keyword, "storeCode", "storeName", "managerName"));
		if (leaseExpiringWithinDays != null)
			spec = spec.and(leaseEndingBetween(today, checkDays(leaseExpiringWithinDays)));
		Page<TenantStore> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by("golfCourseId", "storeCode")));
		return PageResult.of(result, responder(result.getContent(), today));
	}

	/** 임대 종료일이 오늘 ~ 오늘+days 인 매장(폐점 제외), 종료일 빠른 순 */
	@Transactional(readOnly = true)
	public List<TenantStoreResponse> leaseExpiring(int days, Long golfCourseId) {
		LocalDate today = today();
		Specification<TenantStore> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				leaseEndingBetween(today, checkDays(days)),
				(root, q, cb) -> cb.notEqual(root.get("status"), StoreStatus.CLOSED));
		List<TenantStore> list = repo.findAll(spec, Sort.by("leaseEndDate", "storeName"));
		return list.stream().map(responder(list, today)).toList();
	}

	@Transactional(readOnly = true)
	public TenantStoreResponse get(long id) {
		TenantStore s = find(id);
		return responder(List.of(s), today()).apply(s);
	}

	/** 브랜드를 지정하면 사용 중인 브랜드여야 한다. 등록 시 상태는 ACTIVE. */
	public TenantStoreResponse create(TenantStoreCreateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		String code = r.storeCode().trim();
		if (repo.existsByGolfCourseIdAndStoreCode(r.golfCourseId(), code))
			throw new ConflictException("이 골프장에 이미 있는 매장 코드입니다: " + code);
		if (r.storeBrandId() != null)
			requireActiveBrand(r.storeBrandId());
		LocalDateTime now = now();
		Long actor = CurrentUser.idOrNull();
		TenantStore s = new TenantStore(r.golfCourseId(), code, actor, now);
		s.update(r.storeBrandId(), r.storeName().trim(), r.storeType(), blankToNull(r.location()),
				r.leaseStartDate(), r.leaseEndDate(), r.monthlyRent(),
				r.externalTenant() == null || r.externalTenant(), blankToNull(r.managerName()),
				blankToNull(r.managerPhone()), blankToNull(r.memo()), actor, now);
		TenantStore saved = repo.save(s);
		return responder(List.of(saved), today()).apply(saved);
	}

	/** 기본 정보 + 임대 정보 수정. 브랜드를 바꾸면 새 브랜드는 사용 중이어야 한다. 폐점 매장은 수정 불가. */
	public TenantStoreResponse update(long id, TenantStoreUpdateRequest r) {
		TenantStore s = find(id);
		if (r.storeBrandId() != null && !r.storeBrandId().equals(s.getStoreBrandId()))
			requireActiveBrand(r.storeBrandId());
		s.update(r.storeBrandId(), r.storeName().trim(), r.storeType(), blankToNull(r.location()),
				r.leaseStartDate(), r.leaseEndDate(), r.monthlyRent(), r.externalTenant(),
				blankToNull(r.managerName()), blankToNull(r.managerPhone()), blankToNull(r.memo()),
				CurrentUser.idOrNull(), now());
		return responder(List.of(s), today()).apply(s);
	}

	/**
	 * ACTIVE/INACTIVE/SUSPENDED/CLOSED. CLOSED 는 최종 상태(이후 변경 409).
	 * ACTIVE 로 바꿀 때 브랜드가 사용 중지 상태면 409. 변경마다 감사 로그(before/after).
	 */
	public TenantStoreResponse changeStatus(long id, StoreStatus status, String reason) {
		TenantStore s = find(id);
		StoreStatus before = s.getStatus();
		if (before == status && status != StoreStatus.CLOSED)
			return responder(List.of(s), today()).apply(s);
		if (status == StoreStatus.ACTIVE && s.getStoreBrandId() != null) {
			StoreBrand b = lockBrand(s.getStoreBrandId());
			if (!b.isActive())
				throw new ConflictException(
						"사용 중지된 브랜드(" + b.getBrandCode() + ")의 매장은 영업 중으로 바꿀 수 없습니다. 브랜드를 먼저 사용으로 바꾸세요.");
		}
		s.changeStatus(status, CurrentUser.idOrNull(), now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("storeCode", s.getStoreCode());
		details.put("before", before);
		details.put("after", status);
		String why = blankToNull(reason);
		if (why != null)
			details.put("reason", why);
		audit.log("STATUS_CHANGE", "tenant_store", id, details);
		return responder(List.of(s), today()).apply(s);
	}

	private void requireActiveBrand(long brandId) {
		StoreBrand b = lockBrand(brandId);
		if (!b.isActive())
			throw new ConflictException("사용 중지된 브랜드입니다: " + b.getBrandCode());
	}

	/** 브랜드 사용 중지와 동시에 처리되지 않도록 브랜드 행을 잠근다. */
	private StoreBrand lockBrand(long brandId) {
		return brands.findByIdForUpdate(brandId).orElseThrow(() -> NotFoundException.of("입점 브랜드", brandId));
	}

	/** 골프장 이름·브랜드 이름을 한 번에 읽어 응답으로 바꾸는 함수 */
	private Function<TenantStore, TenantStoreResponse> responder(Collection<TenantStore> list, LocalDate today) {
		Map<Long, String> courseNames = golfCourses.names(
				list.stream().map(TenantStore::getGolfCourseId).collect(Collectors.toSet()));
		List<Long> brandIds = list.stream().map(TenantStore::getStoreBrandId).filter(Objects::nonNull).distinct()
				.toList();
		Map<Long, String> brandNames = brandIds.isEmpty() ? Map.of()
				: brands.findAllById(brandIds).stream()
						.collect(Collectors.toMap(StoreBrand::getId, StoreBrand::getBrandName));
		return s -> TenantStoreResponse.of(s, courseNames.get(s.getGolfCourseId()),
				s.getStoreBrandId() == null ? null : brandNames.get(s.getStoreBrandId()), today);
	}

	private static Specification<TenantStore> leaseEndingBetween(LocalDate today, int days) {
		return Specification.allOf(Specs.gte("leaseEndDate", today), Specs.lte("leaseEndDate", today.plusDays(days)));
	}

	private static int checkDays(int days) {
		if (days < 0 || days > MAX_LEASE_DAYS)
			throw new IllegalArgumentException("임대 만료 조회 기간은 0~" + MAX_LEASE_DAYS + "일이어야 합니다.");
		return days;
	}

	private TenantStore find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("입점 매장", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
