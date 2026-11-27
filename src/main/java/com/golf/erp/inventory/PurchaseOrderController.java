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

@Tag(name = "발주")
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
	private final PurchaseOrderService service;

	public PurchaseOrderController(PurchaseOrderService service) {
		this.service = service;
	}

	@Operation(summary = "발주 목록", description = "keyword: 발주번호·거래처, from/to: 발주일(양 끝 포함)")
	@GetMapping
	public PageResult<PoSummary> list(@RequestParam(required = false) PurchaseOrderStatus status,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, keyword, from, to, page, size);
	}

	@Operation(summary = "발주 상세(품목 포함)")
	@GetMapping("/{id}")
	public PoResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "발주서 작성", description = "DRAFT 로 만든다. total_amount = Σ 발주 수량 × 단가")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PoResponse create(@Valid @RequestBody PoRequest r) {
		return service.create(r);
	}

	@Operation(summary = "발주서 수정", description = "DRAFT 상태에서만. 품목 목록 전체를 요청 내용으로 바꾼다")
	@PutMapping("/{id}")
	public PoResponse update(@PathVariable long id, @Valid @RequestBody PoRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "발주", description = "DRAFT → ORDERED, 발주일을 오늘로 기록")
	@PostMapping("/{id}/order")
	public PoResponse order(@PathVariable long id) {
		return service.order(id);
	}

	@Operation(summary = "입고", description = "줄마다 입고 수량(남은 발주 수량 이하)을 받아 RECEIPT 수불을 발주 단가로 만든다. 전량 입고 시 RECEIVED, 아니면 PARTIAL")
	@PostMapping("/{id}/receive")
	public PoResponse receive(@PathVariable long id, @Valid @RequestBody ReceiveRequest r) {
		return service.receive(id, r);
	}

	@Operation(summary = "발주 취소", description = "DRAFT·ORDERED 이고 입고가 없을 때만")
	@PostMapping("/{id}/cancel")
	public PoResponse cancel(@PathVariable long id) {
		return service.cancel(id);
	}
}
