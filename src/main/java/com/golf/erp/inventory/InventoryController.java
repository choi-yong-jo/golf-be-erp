package com.golf.erp.inventory;

import com.golf.erp.common.PageResult;
import com.golf.erp.inventory.InventoryDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "재고")
@RestController
@RequestMapping("/api")
public class InventoryController {
	private final InventoryItemService items;
	private final InventoryTransactionService transactions;

	public InventoryController(InventoryItemService items, InventoryTransactionService transactions) {
		this.items = items;
		this.transactions = transactions;
	}

	@Operation(summary = "재고 품목 목록", description = "keyword: 코드·이름. 현재고(currentStock)와 재주문 필요 여부(belowReorder) 포함")
	@GetMapping("/inventory-items")
	public PageResult<ItemResponse> items(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return items.list(active, keyword, page, size);
	}

	@Operation(summary = "재주문 필요 품목", description = "사용 중이고 재주문 기준(>0)이 있으며 현재고가 기준 이하인 품목")
	@GetMapping("/inventory-items/low-stock")
	public List<ItemResponse> lowStock() {
		return items.lowStock();
	}

	@GetMapping("/inventory-items/{id}")
	public ItemResponse item(@PathVariable long id) {
		return items.get(id);
	}

	@PostMapping("/inventory-items")
	@ResponseStatus(HttpStatus.CREATED)
	public ItemResponse createItem(@Valid @RequestBody ItemRequest r) {
		return items.create(r);
	}

	@PutMapping("/inventory-items/{id}")
	public ItemResponse updateItem(@PathVariable long id, @Valid @RequestBody ItemRequest r) {
		return items.update(id, r);
	}

	@PatchMapping("/inventory-items/{id}/active")
	public ItemResponse changeItemActive(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
		return items.changeActive(id, r.active());
	}

	@Operation(summary = "재고 품목 비활성화", description = "물리 삭제하지 않고 is_active=false 로 바꾼다")
	@DeleteMapping("/inventory-items/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deactivateItem(@PathVariable long id) {
		items.changeActive(id, false);
	}

	@Operation(summary = "재고 수불 목록", description = "from/to: 발생 일자(양 끝 포함). adjustmentOnly=true: 실사 조정 거래만"
			+ "(ADJUSTMENT + memo 가 '[조정]' 으로 시작하는 ISSUE)")
	@GetMapping("/inventory-transactions")
	public PageResult<TransactionResponse> transactions(@RequestParam(required = false) Long inventoryItemId,
			@RequestParam(required = false) Long facilityId,
			@RequestParam(required = false) InventoryTransactionType type,
			@RequestParam(required = false) Boolean adjustmentOnly,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return transactions.list(inventoryItemId, facilityId, type, adjustmentOnly, from, to, page, size);
	}

	@GetMapping("/inventory-transactions/{id}")
	public TransactionResponse transaction(@PathVariable long id) {
		return transactions.get(id);
	}

	@Operation(summary = "재고 수불 등록", description = """
			RECEIPT(입고, +), RETURN(영업장 반납, +), ISSUE(출고, −), ADJUSTMENT(실사 조정).
			ADJUSTMENT 는 direction 필수: INCREASE 는 ADJUSTMENT(+)로, DECREASE 는 ISSUE(−) + memo '[조정] ...' 로 저장된다.
			재고가 줄어드는 거래로 현재고가 음수가 되면 409. 등록한 수불은 수정·삭제할 수 없다(반대 거래로 정정).""")
	@PostMapping("/inventory-transactions")
	@ResponseStatus(HttpStatus.CREATED)
	public TransactionResponse createTransaction(@Valid @RequestBody TransactionRequest r) {
		return transactions.create(r);
	}
}
