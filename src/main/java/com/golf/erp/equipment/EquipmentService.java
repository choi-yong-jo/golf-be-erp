package com.golf.erp.equipment;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.equipment.EquipmentDtos.EquipmentRequest;
import com.golf.erp.equipment.EquipmentDtos.EquipmentResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class EquipmentService {
	private final EquipmentRepository repo;
	private final EquipmentMaintenanceRepository maintenances;
	private final EquipmentRentalLookup rentals;
	private final RefChecker refs;
	private final AuditLogger audit;

	public EquipmentService(EquipmentRepository repo, EquipmentMaintenanceRepository maintenances,
			EquipmentRentalLookup rentals, RefChecker refs, AuditLogger audit) {
		this.repo = repo;
		this.maintenances = maintenances;
		this.rentals = rentals;
		this.refs = refs;
		this.audit = audit;
	}

	/** keyword: 장비 코드·이름·시리얼 번호·위치 */
	@Transactional(readOnly = true)
	public PageResult<EquipmentResponse> list(Long golfCourseId, String type, EquipmentStatus status, String keyword,
			int page, int size) {
		Specification<Equipment> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("type", type == null ? null : type.trim()), Specs.eq("status", status),
				Specs.like(keyword, "code", "name", "serialNo", "location"));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by("code"))), EquipmentResponse::of);
	}

	@Transactional(readOnly = true)
	public EquipmentResponse get(long id) {
		return EquipmentResponse.of(find(id));
	}

	public EquipmentResponse create(EquipmentRequest r) {
		refs.requireIfPresent(Ref.GOLF_COURSE, r.golfCourseId());
		String code = r.code().trim();
		if (repo.existsByCode(code))
			throw new ConflictException("이미 있는 장비 코드입니다: " + code);
		Equipment e = new Equipment(code);
		apply(e, code, r);
		return EquipmentResponse.of(repo.save(e));
	}

	/** 폐기된 장비는 수정할 수 없다. */
	public EquipmentResponse update(long id, EquipmentRequest r) {
		Equipment e = find(id);
		if (r.golfCourseId() != null && !r.golfCourseId().equals(e.getGolfCourseId()))
			refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		String code = r.code().trim();
		if (repo.existsByCodeAndIdNot(code, id))
			throw new ConflictException("이미 있는 장비 코드입니다: " + code);
		apply(e, code, r);
		return EquipmentResponse.of(e);
	}

	/**
	 * 끝나지 않은 정비가 있으면 MAINTENANCE 외의 상태로 바꿀 수 없다(정비 완료·취소가 먼저).
	 * 렌탈 중인 장비는 정비·폐기로 바꿀 수 없다. RETIRED 는 되돌릴 수 없다.
	 */
	public EquipmentResponse changeStatus(long id, EquipmentStatus status) {
		Equipment e = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("장비", id));
		EquipmentStatus before = e.getStatus();
		if (before == status)
			return EquipmentResponse.of(e);
		e.requireNotRetired();
		if (status != EquipmentStatus.MAINTENANCE
				&& maintenances.existsByEquipmentIdAndStatusIn(id, MaintenanceStatus.ACTIVE))
			throw new ConflictException("끝나지 않은 정비가 있습니다. 정비를 완료하거나 취소하세요.");
		if ((status == EquipmentStatus.RETIRED || status == EquipmentStatus.MAINTENANCE) && rentals.isRentedOut(id))
			throw new ConflictException("렌탈 중인 장비입니다. 반납 후 변경하세요.");
		e.changeStatus(status);
		audit.log("STATUS_CHANGE", "equipment", id, Map.of("before", before, "after", status));
		return EquipmentResponse.of(e);
	}

	/** 잘못 등록한 장비만 삭제할 수 있다. 정비·렌탈 이력이 있으면 409(폐기 처리로 대신). */
	public void delete(long id) {
		Equipment e = find(id);
		if (maintenances.existsByEquipmentId(id) || rentals.isReferenced(id))
			throw new ConflictException("정비 또는 렌탈 이력이 있는 장비는 삭제할 수 없습니다. 폐기(RETIRED) 처리하세요.");
		Map<String, Object> details = new HashMap<>();
		details.put("code", e.getCode());
		details.put("name", e.getName());
		repo.delete(e);
		audit.log("DELETE", "equipment", id, details);
	}

	private void apply(Equipment e, String code, EquipmentRequest r) {
		e.update(r.golfCourseId(), code, r.name().trim(), r.type().trim(), blankToNull(r.serialNo()),
				r.purchaseDate(), blankToNull(r.location()), blankToNull(r.memo()));
	}

	private Equipment find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("장비", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
