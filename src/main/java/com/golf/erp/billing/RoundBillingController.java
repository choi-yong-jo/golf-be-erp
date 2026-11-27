package com.golf.erp.billing;

import com.golf.erp.billing.BillingDtos.BillResponse;
import com.golf.erp.billing.BillingDtos.ChargeCreateRequest;
import com.golf.erp.billing.BillingDtos.ChargeResponse;
import com.golf.erp.billing.BillingDtos.PaymentCreateRequest;
import com.golf.erp.billing.BillingDtos.PaymentResponse;
import com.golf.erp.billing.BillingDtos.ReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 라운드 요금·수납(권한은 /api/rounds/** = ROUND) */
@Tag(name = "라운드 요금·수납")
@RestController
@RequestMapping("/api/rounds/{roundId}")
public class RoundBillingController {
	private final BillingService service;

	public RoundBillingController(BillingService service) {
		this.service = service;
	}

	@Operation(summary = "계산서", description = "요금·수납 목록, 참가자별·팀 공통 합계, 잔액(요금 - 수납)")
	@GetMapping("/bill")
	public BillResponse bill(@PathVariable long roundId) {
		return service.bill(roundId);
	}

	@GetMapping("/charges")
	public List<ChargeResponse> charges(@PathVariable long roundId) {
		return service.charges(roundId);
	}

	@Operation(summary = "요금 추가", description = "serviceFeeId 로 부대 요금표에서, 또는 chargeType·unitPrice 직접 입력. "
			+ "1인당 요금을 참가자 지정 없이 고르면 참가자마다 한 건씩 만든다. 일 마감된 날이면 409")
	@PostMapping("/charges")
	@ResponseStatus(HttpStatus.CREATED)
	public List<ChargeResponse> addCharge(@PathVariable long roundId, @Valid @RequestBody ChargeCreateRequest r) {
		return service.addCharge(roundId, r);
	}

	@Operation(summary = "요금 취소", description = "지우지 않고 is_void 로 표시. 일 마감된 날이면 409")
	@PostMapping("/charges/{chargeId}/void")
	public ChargeResponse voidCharge(@PathVariable long roundId, @PathVariable long chargeId,
			@Valid @RequestBody ReasonRequest r) {
		return service.voidCharge(roundId, chargeId, r.reason());
	}

	@GetMapping("/payments")
	public List<PaymentResponse> payments(@PathVariable long roundId) {
		return service.payments(roundId);
	}

	@Operation(summary = "수납", description = "일 마감된 날이면 409")
	@PostMapping("/payments")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentResponse addPayment(@PathVariable long roundId, @Valid @RequestBody PaymentCreateRequest r) {
		return service.addPayment(roundId, r);
	}

	@Operation(summary = "수납 취소", description = "일 마감된 날이면 409")
	@PostMapping("/payments/{paymentId}/cancel")
	public PaymentResponse cancelPayment(@PathVariable long roundId, @PathVariable long paymentId,
			@Valid @RequestBody ReasonRequest r) {
		return service.cancelPayment(roundId, paymentId, r.reason());
	}
}
