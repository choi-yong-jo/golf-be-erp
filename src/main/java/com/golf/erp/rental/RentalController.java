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

@Tag(name = "장비 렌탈")
@RestController
@RequestMapping("/api/rentals")
public class RentalController {
	private final RentalService rentals;

	public RentalController(RentalService rentals) {
		this.rentals = rentals;
	}

	@Operation(summary = "렌탈 목록", description = "from/to: 접수일. keyword: 대여자 이름·전화번호·렌탈 번호. "
			+ "overdue=true: 대여 중이면서 반납 기한이 지난 것")
	@GetMapping
	public PageResult<RentalSummaryResponse> list(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) RentalStatus status, @RequestParam(required = false) Long memberId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) Boolean overdue,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return rentals.list(golfCourseId, status, memberId, from, to, keyword, overdue, page, size);
	}

	@Operation(summary = "렌탈 상세", description = "품목·결제·이력·수납 현황 포함")
	@GetMapping("/{id}")
	public RentalDetailResponse get(@PathVariable long id) {
		return rentals.get(id);
	}

	@Operation(summary = "렌탈 접수", description = "RESERVED 로 만든다. 요금·보증금 생략 시 품목 마스터 값 복사. 재고는 출고 때 확인")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RentalDetailResponse create(@Valid @RequestBody RentalCreateRequest r) {
		return rentals.create(r);
	}

	@Operation(summary = "출고", description = "RESERVED → RENTED, 가용 → 대여 중. dueAt 생략 시 오늘 + (최대 대여 일수 - 1)일 23:59:59")
	@PostMapping("/{id}/issue")
	public RentalDetailResponse issue(@PathVariable long id,
			@Valid @RequestBody(required = false) RentalIssueRequest r) {
		return rentals.issue(id, r);
	}

	@Operation(summary = "반납(전체·부분)", description = "품목별 returnedQuantity(정상→가용), damagedQuantity(파손→정비 중), "
			+ "lostQuantity(분실→총 수량 차감), damageFee(추가 변상금)")
	@PostMapping("/{id}/return")
	public RentalDetailResponse returnItems(@PathVariable long id, @Valid @RequestBody RentalReturnRequest r) {
		return rentals.returnItems(id, r);
	}

	@Operation(summary = "반납 기한 연장", description = "addRentalDays 를 주면 미반납 품목 대여 일수 증가·대여료 재계산")
	@PostMapping("/{id}/extend")
	public RentalDetailResponse extend(@PathVariable long id, @Valid @RequestBody RentalExtendRequest r) {
		return rentals.extend(id, r);
	}

	@Operation(summary = "품목 요금 정정", description = "일 대여료·대여 일수·파손료(누적값 대체)")
	@PatchMapping("/{id}/items/{itemId}/fees")
	public RentalDetailResponse changeItemFees(@PathVariable long id, @PathVariable long itemId,
			@Valid @RequestBody RentalItemFeeRequest r) {
		return rentals.changeItemFees(id, itemId, r);
	}

	@Operation(summary = "렌탈 취소", description = "출고 전(RESERVED)만 가능. 받은 돈은 REFUND 결제로 돌려준다")
	@PostMapping("/{id}/cancel")
	public RentalDetailResponse cancel(@PathVariable long id, @Valid @RequestBody(required = false) ReasonRequest r) {
		return rentals.cancel(id, r == null ? null : r.reason());
	}

	@Operation(summary = "연체 표시", description = "반납 기한이 지난 RENTED·PARTIALLY_RETURNED → OVERDUE. golfCourseId 생략 시 전체")
	@PostMapping("/mark-overdue")
	public MarkOverdueResult markOverdue(@RequestParam(required = false) Long golfCourseId) {
		return rentals.markOverdue(golfCourseId);
	}

	@Operation(summary = "결제·환불 기록", description = "RENTAL_FEE, DEPOSIT, DAMAGE_FEE, REFUND(보증금 반환 등). 한도 초과 시 409")
	@PostMapping("/{id}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public RentalDetailResponse addPayment(@PathVariable long id, @Valid @RequestBody RentalPaymentRequest r) {
		return rentals.addPayment(id, r);
	}

	@Operation(summary = "결제 취소")
	@PostMapping("/{id}/payments/{paymentId}/cancel")
	public RentalDetailResponse cancelPayment(@PathVariable long id, @PathVariable long paymentId,
			@Valid @RequestBody(required = false) ReasonRequest r) {
		return rentals.cancelPayment(id, paymentId, r == null ? null : r.reason());
	}
}
