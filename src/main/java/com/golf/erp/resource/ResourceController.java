package com.golf.erp.resource;

import com.golf.erp.common.PageResult;
import com.golf.erp.resource.ResourceDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "캐디·카트")
@RestController
@RequestMapping("/api")
public class ResourceController {
	private final CaddieService caddies;
	private final CartService carts;

	public ResourceController(CaddieService caddies, CartService carts) {
		this.caddies = caddies;
		this.carts = carts;
	}

	@Operation(summary = "캐디 목록", description = "available=true: 근무 중이고 지금 배정되지 않은 캐디(배정 후보). keyword: 이름·번호")
	@GetMapping("/caddies")
	public PageResult<CaddieResponse> caddies(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) CaddieStatus status, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) Boolean available, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return caddies.list(golfCourseId, status, keyword, available, page, size);
	}

	@GetMapping("/caddies/{id}")
	public CaddieResponse caddie(@PathVariable long id) {
		return caddies.get(id);
	}

	@PostMapping("/caddies")
	@ResponseStatus(HttpStatus.CREATED)
	public CaddieResponse createCaddie(@Valid @RequestBody CaddieCreateRequest r) {
		return caddies.create(r);
	}

	@PutMapping("/caddies/{id}")
	public CaddieResponse updateCaddie(@PathVariable long id, @Valid @RequestBody CaddieUpdateRequest r) {
		return caddies.update(id, r);
	}

	@Operation(summary = "캐디 근무 상태 변경", description = "ACTIVE, ON_LEAVE, RETIRED. 배정 중이면 409")
	@PatchMapping("/caddies/{id}/status")
	public CaddieResponse changeCaddieStatus(@PathVariable long id, @Valid @RequestBody CaddieStatusRequest r) {
		return caddies.changeStatus(id, r.status());
	}

	@Operation(summary = "카트 목록", description = "available=true: ACTIVE 이고 지금 배정되지 않은 카트")
	@GetMapping("/carts")
	public PageResult<CartResponse> carts(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) CartStatus status, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) Boolean available, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return carts.list(golfCourseId, status, keyword, available, page, size);
	}

	@GetMapping("/carts/{id}")
	public CartResponse cart(@PathVariable long id) {
		return carts.get(id);
	}

	@PostMapping("/carts")
	@ResponseStatus(HttpStatus.CREATED)
	public CartResponse createCart(@Valid @RequestBody CartCreateRequest r) {
		return carts.create(r);
	}

	@PutMapping("/carts/{id}")
	public CartResponse updateCart(@PathVariable long id, @Valid @RequestBody CartUpdateRequest r) {
		return carts.update(id, r);
	}

	@Operation(summary = "카트 상태 변경", description = "ACTIVE, MAINTENANCE, RETIRED. MAINTENANCE→ACTIVE 시 마지막 정비 시각 기록")
	@PatchMapping("/carts/{id}/status")
	public CartResponse changeCartStatus(@PathVariable long id, @Valid @RequestBody CartStatusRequest r) {
		return carts.changeStatus(id, r.status());
	}
}
