package com.golf.erp.rental;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.rental.RentalDtos.RentalEquipmentCreateRequest;
import com.golf.erp.rental.RentalDtos.RentalEquipmentFlagsRequest;
import com.golf.erp.rental.RentalDtos.RentalEquipmentResponse;
import com.golf.erp.rental.RentalDtos.RentalEquipmentUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.golf.erp.security.CurrentUser;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/** 렌탈 품목 마스터 */
@Service
@Transactional
public class RentalEquipmentService {
	private final RentalEquipmentRepository repo;
	private final AuditLogger audit;
	private final Clock clock;

	public RentalEquipmentService(RentalEquipmentRepository repo, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 코드·이름·브랜드·모델명 */
	@Transactional(readOnly = true)
	public PageResult<RentalEquipmentResponse> list(EquipmentCategory category, String keyword, Boolean rentable,
			Boolean active, int page, int size) {
		Specification<RentalEquipment> spec = Specification.allOf(Specs.eq("category", category),
				Specs.eq("rentable", rentable), Specs.eq("active", active),
				Specs.like(keyword, "code", "name", "brand", "modelName"));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by("category", "code"))),
				RentalEquipmentResponse::of);
	}

	@Transactional(readOnly = true)
	public RentalEquipmentResponse get(long id) {
		return RentalEquipmentResponse.of(find(id));
	}

	public RentalEquipmentResponse create(RentalEquipmentCreateRequest r) {
		String code = r.code().trim();
		if (repo.existsByCode(code))
			throw new ConflictException("이미 있는 장비 코드입니다: " + code);
		RentalEquipment e = new RentalEquipment(code, CurrentUser.idOrNull(), LocalDateTime.now(clock));
		e.update(r.name().trim(), r.category(), blankToNull(r.brand()), blankToNull(r.modelName()),
				blankToNull(r.sizeLabel()), blankToNull(r.description()), r.dailyRentalFee(), r.depositAmount(),
				r.rentable() == null || r.rentable());
		return RentalEquipmentResponse.of(repo.save(e));
	}

	/** 요금 변경은 이후 접수하는 렌탈부터 적용된다(기존 렌탈 품목은 접수 시점 금액 유지). */
	public RentalEquipmentResponse update(long id, RentalEquipmentUpdateRequest r) {
		RentalEquipment e = find(id);
		if (!e.isActive() && r.rentable())
			throw new ConflictException("비활성 장비는 대여 가능으로 바꿀 수 없습니다. 먼저 활성화하세요.");
		e.update(r.name().trim(), r.category(), blankToNull(r.brand()), blankToNull(r.modelName()),
				blankToNull(r.sizeLabel()), blankToNull(r.description()), r.dailyRentalFee(), r.depositAmount(),
				r.rentable());
		touch(e);
		return RentalEquipmentResponse.of(e);
	}

	public RentalEquipmentResponse changeFlags(long id, RentalEquipmentFlagsRequest r) {
		RentalEquipment e = find(id);
		boolean beforeActive = e.isActive();
		boolean beforeRentable = e.isRentable();
		e.changeFlags(r.active(), r.rentable());
		touch(e);
		audit.log("STATUS_CHANGE", "rental_equipment", id, Map.of("activeBefore", beforeActive, "activeAfter",
				e.isActive(), "rentableBefore", beforeRentable, "rentableAfter", e.isRentable()));
		return RentalEquipmentResponse.of(e);
	}

	/** 삭제 대신 비활성화(재고·렌탈 이력이 남아 있으므로). 이미 대여 중인 수량은 정상 반납할 수 있다. */
	public void deactivate(long id) {
		RentalEquipment e = find(id);
		if (!e.isActive())
			return;
		e.deactivate();
		touch(e);
		audit.log("DELETE", "rental_equipment", id, Map.of("code", e.getCode()));
	}

	private void touch(RentalEquipment e) {
		e.touch(CurrentUser.idOrNull(), LocalDateTime.now(clock));
	}

	private RentalEquipment find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("렌탈 장비", id));
	}

	static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
