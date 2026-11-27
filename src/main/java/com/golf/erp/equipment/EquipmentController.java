package com.golf.erp.equipment;

import com.golf.erp.common.PageResult;
import com.golf.erp.equipment.EquipmentDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "장비")
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {
	private final EquipmentService equipment;
	private final EquipmentMaintenanceService maintenances;

	public EquipmentController(EquipmentService equipment, EquipmentMaintenanceService maintenances) {
		this.equipment = equipment;
		this.maintenances = maintenances;
	}

	@Operation(summary = "장비 목록", description = "type: 장비 분류(정확히 일치), keyword: 코드·이름·시리얼 번호·위치")
	@GetMapping
	public PageResult<EquipmentResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) String type, @RequestParam(required = false) EquipmentStatus status,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return equipment.list(golfCourseId, type, status, keyword, page, size);
	}

	@GetMapping("/{id}")
	public EquipmentResponse get(@PathVariable long id) {
		return equipment.get(id);
	}

	@Operation(summary = "장비 등록", description = "장비 코드 중복이면 409. golfCourseId 를 비우면 공용 장비")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EquipmentResponse create(@Valid @RequestBody EquipmentRequest r) {
		return equipment.create(r);
	}

	@Operation(summary = "장비 수정", description = "폐기(RETIRED)된 장비는 수정 불가(409)")
	@PutMapping("/{id}")
	public EquipmentResponse update(@PathVariable long id, @Valid @RequestBody EquipmentRequest r) {
		return equipment.update(id, r);
	}

	@Operation(summary = "장비 상태 변경", description = "AVAILABLE, IN_USE, MAINTENANCE, RETIRED. RETIRED 는 되돌릴 수 없음."
			+ " 끝나지 않은 정비가 있으면 MAINTENANCE 외로 변경 불가, 렌탈 중이면 정비·폐기 불가")
	@PatchMapping("/{id}/status")
	public EquipmentResponse changeStatus(@PathVariable long id, @Valid @RequestBody EquipmentStatusRequest r) {
		return equipment.changeStatus(id, r.status());
	}

	@Operation(summary = "장비 삭제", description = "잘못 등록한 장비만. 정비·렌탈 이력이 있으면 409(폐기 처리 사용)")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		equipment.delete(id);
	}

	// ---- 정비 ----

	@Operation(summary = "장비별 정비 이력")
	@GetMapping("/{id}/maintenances")
	public PageResult<MaintenanceResponse> maintenancesOf(@PathVariable long id,
			@RequestParam(required = false) MaintenanceStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return maintenances.listForEquipment(id, status, page, size);
	}

	@Operation(summary = "정비 등록", description = "장비가 MAINTENANCE 로 바뀐다. 폐기·사용 중·렌탈 중 장비는 409")
	@PostMapping("/{id}/maintenances")
	@ResponseStatus(HttpStatus.CREATED)
	public MaintenanceResponse createMaintenance(@PathVariable long id, @Valid @RequestBody MaintenanceRequest r) {
		return maintenances.create(id, r);
	}

	@Operation(summary = "정비 목록(전체)", description = "from/to: 접수일 범위(포함)")
	@GetMapping("/maintenances")
	public PageResult<MaintenanceResponse> maintenanceList(@RequestParam(required = false) MaintenanceStatus status,
			@RequestParam(required = false) Long golfCourseId, @RequestParam(required = false) Long equipmentId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return maintenances.list(equipmentId, golfCourseId, status, from, to, page, size);
	}

	@GetMapping("/maintenances/{maintenanceId}")
	public MaintenanceResponse maintenance(@PathVariable long maintenanceId) {
		return maintenances.get(maintenanceId);
	}

	@Operation(summary = "정비 내용 수정", description = "끝나지 않은 정비만")
	@PutMapping("/maintenances/{maintenanceId}")
	public MaintenanceResponse updateMaintenance(@PathVariable long maintenanceId,
			@Valid @RequestBody MaintenanceRequest r) {
		return maintenances.update(maintenanceId, r);
	}

	@Operation(summary = "정비 시작", description = "OPEN → IN_PROGRESS")
	@PostMapping("/maintenances/{maintenanceId}/start")
	public MaintenanceResponse startMaintenance(@PathVariable long maintenanceId) {
		return maintenances.start(maintenanceId);
	}

	@Operation(summary = "정비 완료", description = "비용·업체 기록. 장비의 끝나지 않은 정비가 더 없으면 장비가 AVAILABLE 로 돌아온다")
	@PostMapping("/maintenances/{maintenanceId}/complete")
	public MaintenanceResponse completeMaintenance(@PathVariable long maintenanceId,
			@Valid @RequestBody(required = false) MaintenanceCompleteRequest r) {
		return maintenances.complete(maintenanceId, r);
	}

	@Operation(summary = "정비 취소", description = "장비의 끝나지 않은 정비가 더 없으면 장비가 AVAILABLE 로 돌아온다")
	@PostMapping("/maintenances/{maintenanceId}/cancel")
	public MaintenanceResponse cancelMaintenance(@PathVariable long maintenanceId) {
		return maintenances.cancel(maintenanceId);
	}
}
