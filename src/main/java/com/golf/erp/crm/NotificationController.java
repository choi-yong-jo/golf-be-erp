package com.golf.erp.crm;

import com.golf.erp.common.PageResult;
import com.golf.erp.crm.CrmDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 고객 알림. 실제 발송은 이 서버가 하지 않는다. 외부 발송기는 CRM_WRITE 권한을 가진 직원 계정으로
 * GET /queue 를 주기적으로 가져가 보내고, 결과를 POST /{id}/mark-sent 또는 /{id}/mark-failed 로 기록한다.
 */
@Tag(name = "고객 알림")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
	private final NotificationService service;

	public NotificationController(NotificationService service) {
		this.service = service;
	}

	@Operation(summary = "알림 목록", description = "from/to: 예약 발송 시각 또는 발송 시각이 그 일자 범위 안(양 끝 포함)")
	@GetMapping
	public PageResult<NotificationResponse> list(@RequestParam(required = false) NotificationStatus status,
			@RequestParam(required = false) NotificationChannel channel,
			@RequestParam(required = false) Long memberId, @RequestParam(required = false) Long reservationId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(status, channel, memberId, reservationId, from, to, page, size);
	}

	@Operation(summary = "발송 대기열", description = "외부 발송기용. QUEUED 이면서 예약 시각이 없거나 지난 알림(등록 순, 최대 limit 건, limit 1~500)")
	@GetMapping("/queue")
	public List<NotificationResponse> queue(@RequestParam(defaultValue = "100") int limit) {
		return service.queue(limit);
	}

	@GetMapping("/{id}")
	public NotificationResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "알림 등록", description = "QUEUED 로 만든다. scheduledAt 을 비우면 즉시 발송 대상")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public NotificationResponse create(@Valid @RequestBody NotificationCreateRequest r) {
		return service.create(r);
	}

	@Operation(summary = "알림 취소", description = "QUEUED 에서만")
	@PostMapping("/{id}/cancel")
	public NotificationResponse cancel(@PathVariable long id) {
		return service.cancel(id);
	}

	@Operation(summary = "발송 완료 기록(외부 발송기)", description = "QUEUED → SENT, sent_at 기록")
	@PostMapping("/{id}/mark-sent")
	public NotificationResponse markSent(@PathVariable long id, @Valid @RequestBody(required = false) MarkSentRequest r) {
		return service.markSent(id, r == null ? null : r.providerMessageId());
	}

	@Operation(summary = "발송 실패 기록(외부 발송기)", description = "QUEUED → FAILED, 오류 메시지 필수")
	@PostMapping("/{id}/mark-failed")
	public NotificationResponse markFailed(@PathVariable long id, @Valid @RequestBody MarkFailedRequest r) {
		return service.markFailed(id, r);
	}

	@Operation(summary = "재시도", description = "FAILED → QUEUED. scheduledAt 을 비우면 즉시")
	@PostMapping("/{id}/retry")
	public NotificationResponse retry(@PathVariable long id, @Valid @RequestBody(required = false) RetryRequest r) {
		return service.retry(id, r == null ? null : r.scheduledAt());
	}
}
