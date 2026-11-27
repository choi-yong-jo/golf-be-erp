package com.golf.erp.equipment;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.equipment.EquipmentDtos.MaintenanceCompleteRequest;
import com.golf.erp.equipment.EquipmentDtos.MaintenanceRequest;
import com.golf.erp.equipment.EquipmentDtos.MaintenanceResponse;
import com.golf.erp.security.CurrentUser;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 장비 정비. 정비를 등록하면 장비가 MAINTENANCE 가 되고, 그 장비의 끝나지 않은 정비가 모두 완료·취소되면 AVAILABLE 로 돌아온다.
 * 장비 상태와 함께 바뀌므로 장비 행을 잠근 뒤 처리한다.
 */
@Service
@Transactional
public class EquipmentMaintenanceService {
	private final EquipmentMaintenanceRepository repo;
	private final EquipmentRepository equipmentRepo;
	private final EquipmentRentalLookup rentals;
	private final AuditLogger audit;
	private final Clock clock;

	public EquipmentMaintenanceService(EquipmentMaintenanceRepository repo, EquipmentRepository equipmentRepo,
			EquipmentRentalLookup rentals, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.equipmentRepo = equipmentRepo;
		this.rentals = rentals;
		this.audit = audit;
		this.clock = clock;
	}

	/** from/to: 접수일(reported_at) 범위(포함) */
	@Transactional(readOnly = true)
	public PageResult<MaintenanceResponse> list(Long equipmentId, Long golfCourseId, MaintenanceStatus status,
			LocalDate from, LocalDate to, int page, int size) {
		Specification<EquipmentMaintenance> spec = Specification.allOf(Specs.eq("equipmentId", equipmentId),
				Specs.eq("status", status), Specs.gte("reportedAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("reportedAt", to == null ? null : to.plusDays(1).atStartOfDay()), inGolfCourse(golfCourseId));
		Page<EquipmentMaintenance> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("reportedAt"), Sort.Order.desc("id"))));
		Map<Long, Equipment> equipment = equipmentRepo
				.findAllById(result.getContent().stream().map(EquipmentMaintenance::getEquipmentId).distinct().toList())
				.stream().collect(Collectors.toMap(Equipment::getId, Function.identity()));
		return PageResult.of(result, m -> MaintenanceResponse.of(m, equipment.get(m.getEquipmentId())));
	}

	/** 장비 상세 화면의 정비 이력 */
	@Transactional(readOnly = true)
	public PageResult<MaintenanceResponse> listForEquipment(long equipmentId, MaintenanceStatus status, int page,
			int size) {
		if (!equipmentRepo.existsById(equipmentId))
			throw NotFoundException.of("장비", equipmentId);
		return list(equipmentId, null, status, null, null, page, size);
	}

	@Transactional(readOnly = true)
	public MaintenanceResponse get(long id) {
		EquipmentMaintenance m = find(id);
		return MaintenanceResponse.of(m, equipmentRepo.findById(m.getEquipmentId()).orElse(null));
	}

	/** 폐기 장비·렌탈 중이거나 사용 중(IN_USE)인 장비는 정비를 등록할 수 없다. 등록하면 장비가 MAINTENANCE 가 된다. */
	public MaintenanceResponse create(long equipmentId, MaintenanceRequest r) {
		Equipment e = lockEquipment(equipmentId);
		e.requireNotRetired();
		if (e.getStatus() == EquipmentStatus.IN_USE)
			throw new ConflictException("사용 중(IN_USE)인 장비입니다. 사용을 마친 뒤 정비를 등록하세요.");
		if (rentals.isRentedOut(equipmentId))
			throw new ConflictException("렌탈 중인 장비입니다. 반납 후 정비를 등록하세요.");
		EquipmentMaintenance m = new EquipmentMaintenance(equipmentId, CurrentUser.idOrNull(), now());
		m.update(r.type().trim(), blankToNull(r.vendorName()), blankToNull(r.description()));
		repo.save(m);
		if (e.getStatus() != EquipmentStatus.MAINTENANCE)
			e.changeStatus(EquipmentStatus.MAINTENANCE);
		return MaintenanceResponse.of(m, e);
	}

	public MaintenanceResponse update(long id, MaintenanceRequest r) {
		EquipmentMaintenance m = find(id);
		m.update(r.type().trim(), blankToNull(r.vendorName()), blankToNull(r.description()));
		return MaintenanceResponse.of(m, equipmentRepo.findById(m.getEquipmentId()).orElse(null));
	}

	public MaintenanceResponse start(long id) {
		EquipmentMaintenance m = find(id);
		Equipment e = lockEquipment(m.getEquipmentId());
		m.start(now());
		return MaintenanceResponse.of(m, e);
	}

	public MaintenanceResponse complete(long id, MaintenanceCompleteRequest r) {
		EquipmentMaintenance m = find(id);
		Equipment e = lockEquipment(m.getEquipmentId());
		m.complete(r == null ? null : r.cost(), r == null ? null : blankToNull(r.vendorName()), now());
		releaseIfDone(e);
		audit.log("COMPLETE", "equipment_maintenance", id, Map.of("equipmentId", e.getId(), "cost", m.getCost()));
		return MaintenanceResponse.of(m, e);
	}

	public MaintenanceResponse cancel(long id) {
		EquipmentMaintenance m = find(id);
		Equipment e = lockEquipment(m.getEquipmentId());
		MaintenanceStatus before = m.getStatus();
		m.cancel();
		releaseIfDone(e);
		Map<String, Object> details = new HashMap<>();
		details.put("equipmentId", e.getId());
		details.put("before", before);
		audit.log("CANCEL", "equipment_maintenance", id, details);
		return MaintenanceResponse.of(m, e);
	}

	/** 끝나지 않은 정비가 더 없으면 MAINTENANCE 장비를 AVAILABLE 로 되돌린다. */
	private void releaseIfDone(Equipment e) {
		repo.flush();
		if (e.getStatus() == EquipmentStatus.MAINTENANCE
				&& !repo.existsByEquipmentIdAndStatusIn(e.getId(), MaintenanceStatus.ACTIVE))
			e.changeStatus(EquipmentStatus.AVAILABLE);
	}

	private static Specification<EquipmentMaintenance> inGolfCourse(Long golfCourseId) {
		if (golfCourseId == null)
			return null;
		return (root, q, cb) -> {
			Subquery<Long> sq = q.subquery(Long.class);
			Root<Equipment> e = sq.from(Equipment.class);
			sq.select(e.get("id")).where(cb.equal(e.get("golfCourseId"), golfCourseId));
			return root.get("equipmentId").in(sq);
		};
	}

	private Equipment lockEquipment(long equipmentId) {
		return equipmentRepo.findByIdForUpdate(equipmentId).orElseThrow(() -> NotFoundException.of("장비", equipmentId));
	}

	private EquipmentMaintenance find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("정비", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
