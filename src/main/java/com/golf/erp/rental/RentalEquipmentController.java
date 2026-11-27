package com.golf.erp.rental;

import com.golf.erp.common.PageResult;
import com.golf.erp.rental.RentalDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "장비 렌탈 - 품목·재고")
@RestController
@RequestMapping("/api/rental-equipment")
public class RentalEquipmentController {
	private final RentalEquipmentService equipment;
	private final RentalStockService stocks;

	public RentalEquipmentController(RentalEquipmentService equipment, RentalStockService stocks) {
		this.equipment = equipment;
		this.stocks = stocks;
	}

	@Operation(summary = "렌탈 품목 목록", description = "keyword: 코드·이름·브랜드·모델명")
	@GetMapping
	public PageResult<RentalEquipmentResponse> list(@RequestParam(required = false) EquipmentCategory category,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean rentable,
			@RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return equipment.list(category, keyword, rentable, active, page, size);
	}

	@GetMapping("/{id}")
	public RentalEquipmentResponse get(@PathVariable long id) {
		return equipment.get(id);
	}

	@Operation(summary = "렌탈 품목 등록", description = "equipment_code 중복이면 409")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RentalEquipmentResponse create(@Valid @RequestBody RentalEquipmentCreateRequest r) {
		return equipment.create(r);
	}

	@Operation(summary = "렌탈 품목 수정", description = "코드는 바꿀 수 없다. 요금 변경은 이후 접수분부터 적용")
	@PutMapping("/{id}")
	public RentalEquipmentResponse update(@PathVariable long id, @Valid @RequestBody RentalEquipmentUpdateRequest r) {
		return equipment.update(id, r);
	}

	@Operation(summary = "활성·대여 가능 여부 변경", description = "active=false 면 rentable 도 false")
	@PatchMapping("/{id}/status")
	public RentalEquipmentResponse changeFlags(@PathVariable long id,
			@Valid @RequestBody RentalEquipmentFlagsRequest r) {
		return equipment.changeFlags(id, r);
	}

	@Operation(summary = "렌탈 품목 비활성화", description = "물리 삭제 대신 is_active=false, is_rentable=false")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivate(@PathVariable long id) {
		equipment.deactivate(id);
	}

	@Operation(summary = "골프장 재고 현황", description = "품목별 총·가용·대여 중·정비 중 수량. 입고 전 활성 품목은 0 으로 표시")
	@GetMapping("/stocks")
	public List<StockResponse> courseStocks(@RequestParam long golfCourseId,
			@RequestParam(required = false) EquipmentCategory category,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return stocks.courseStocks(golfCourseId, category, includeInactive);
	}

	@Operation(summary = "품목의 골프장별 재고")
	@GetMapping("/{id}/stocks")
	public List<StockResponse> equipmentStocks(@PathVariable long id) {
		return stocks.equipmentStocks(id);
	}

	@Operation(summary = "재고 처리", description = "STOCK_IN(총·가용+), MAINTENANCE_OUT(가용→정비), MAINTENANCE_IN(정비→가용), "
			+ "LOSS(lossFrom=AVAILABLE|MAINTENANCE 에서 차감, 총-), ADJUSTMENT(targetAvailable 로 가용 맞춤, 차이만큼 총 증감). "
			+ "수량이 모자라면 409")
	@PostMapping("/{id}/stocks/{golfCourseId}/transactions")
	@ResponseStatus(HttpStatus.CREATED)
	public StockTransactionResult transact(@PathVariable long id, @PathVariable long golfCourseId,
			@Valid @RequestBody StockTransactionRequest r) {
		return stocks.transact(id, golfCourseId, r);
	}

	@Operation(summary = "재고 변동 기록", description = "from/to: 발생일. referenceType=EQUIPMENT_RENTAL 이면 렌탈 출고·반납분")
	@GetMapping("/stock-transactions")
	public PageResult<StockTxnResponse> transactions(@RequestParam(required = false) Long rentalEquipmentId,
			@RequestParam(required = false) Long golfCourseId, @RequestParam(required = false) StockTxnType type,
			@RequestParam(required = false) String referenceType, @RequestParam(required = false) Long referenceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return stocks.transactions(rentalEquipmentId, golfCourseId, type, referenceType, referenceId, from, to, page,
				size);
	}
}
