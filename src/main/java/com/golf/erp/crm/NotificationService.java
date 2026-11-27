package com.golf.erp.crm;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.crm.CrmDtos.MarkFailedRequest;
import com.golf.erp.crm.CrmDtos.NotificationCreateRequest;
import com.golf.erp.crm.CrmDtos.NotificationResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 고객 알림 대기열. 이 서버는 실제 발송을 하지 않는다.
 * 외부 발송기(SMS·이메일·카카오 연동 워커)가 {@link #queue(int)} 로 보낼 알림을 가져가 발송하고,
 * 결과를 {@link #markSent} / {@link #markFailed} 로 기록해야 한다.
 */
@Service
@Transactional
public class NotificationService {
	public static final int MAX_QUEUE = 500;

	private final CustomerNotificationRepository repo;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public NotificationService(CustomerNotificationRepository repo, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** from/to: 예약 발송 시각(scheduled_at) 또는 발송 시각(sent_at) 중 하나라도 범위 안(일자, 양 끝 포함) */
	@Transactional(readOnly = true)
	public PageResult<NotificationResponse> list(NotificationStatus status, NotificationChannel channel,
			Long memberId, Long reservationId, LocalDate from, LocalDate to, int page, int size) {
		if (from != null && to != null && from.isAfter(to))
			throw new IllegalArgumentException("조회 시작일이 종료일보다 늦습니다.");
		Specification<CustomerNotification> spec = Specification.allOf(Specs.eq("status", status),
				Specs.eq("channel", channel), Specs.eq("memberId", memberId), Specs.eq("reservationId", reservationId),
				dateRange(from, to));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Order.desc("id")))),
				NotificationResponse::of);
	}

	/** 지금 보낼 알림: QUEUED 이면서 예약 시각이 없거나 지났다. 등록 순서(id) 순 */
	@Transactional(readOnly = true)
	public List<NotificationResponse> queue(int limit) {
		if (limit < 1 || limit > MAX_QUEUE)
			throw new IllegalArgumentException("limit 은 1~" + MAX_QUEUE + " 이어야 합니다.");
		LocalDateTime now = LocalDateTime.now(clock);
		Specification<CustomerNotification> spec = Specification.allOf(Specs.eq("status", NotificationStatus.QUEUED),
				(r, q, cb) -> cb.or(cb.isNull(r.get("scheduledAt")),
						cb.lessThanOrEqualTo(r.<LocalDateTime>get("scheduledAt"), now)));
		return repo.findAll(spec, PageRequest.of(0, limit, Sort.by("id"))).map(NotificationResponse::of).getContent();
	}

	@Transactional(readOnly = true)
	public NotificationResponse get(long id) {
		return NotificationResponse.of(repo.findById(id).orElseThrow(() -> NotFoundException.of("알림", id)));
	}

	public NotificationResponse create(NotificationCreateRequest r) {
		refs.requireIfPresent(Ref.MEMBER, r.memberId());
		refs.requireIfPresent(Ref.RESERVATION, r.reservationId());
		String destination = r.destination().trim();
		validateDestination(r.channel(), destination);
		CustomerNotification n = new CustomerNotification(r.memberId(), r.reservationId(), r.channel(),
				blankToNull(r.templateCode()), destination, r.message(), r.scheduledAt());
		return NotificationResponse.of(repo.save(n));
	}

	public NotificationResponse cancel(long id) {
		CustomerNotification n = lock(id);
		n.cancel();
		audit.log("CANCEL", "customer_notification", id, null);
		return NotificationResponse.of(n);
	}

	/** 외부 발송기가 발송 성공을 알린다. */
	public NotificationResponse markSent(long id, String providerMessageId) {
		CustomerNotification n = lock(id);
		n.markSent(blankToNull(providerMessageId), LocalDateTime.now(clock));
		return NotificationResponse.of(n);
	}

	/** 외부 발송기가 발송 실패를 알린다. */
	public NotificationResponse markFailed(long id, MarkFailedRequest r) {
		CustomerNotification n = lock(id);
		n.markFailed(blankToNull(r.providerMessageId()), r.errorMessage().trim());
		return NotificationResponse.of(n);
	}

	public NotificationResponse retry(long id, LocalDateTime scheduledAt) {
		CustomerNotification n = lock(id);
		n.retry(scheduledAt);
		return NotificationResponse.of(n);
	}

	private static Specification<CustomerNotification> dateRange(LocalDate from, LocalDate to) {
		if (from == null && to == null)
			return null;
		LocalDateTime start = from == null ? null : from.atStartOfDay();
		LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
		return (r, q, cb) -> {
			List<Predicate> any = new ArrayList<>();
			for (String attr : List.of("scheduledAt", "sentAt")) {
				List<Predicate> all = new ArrayList<>();
				all.add(cb.isNotNull(r.get(attr)));
				if (start != null)
					all.add(cb.greaterThanOrEqualTo(r.<LocalDateTime>get(attr), start));
				if (end != null)
					all.add(cb.lessThan(r.<LocalDateTime>get(attr), end));
				any.add(cb.and(all.toArray(Predicate[]::new)));
			}
			return cb.or(any.toArray(Predicate[]::new));
		};
	}

	/** 채널별 최소 형식 검사(실제 유효성은 발송기가 판단) */
	private static void validateDestination(NotificationChannel channel, String destination) {
		switch (channel) {
			case EMAIL -> {
				if (!destination.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
					throw new IllegalArgumentException("이메일 주소 형식이 올바르지 않습니다: " + destination);
			}
			case SMS, KAKAO -> {
				if (!destination.matches("^[0-9+\\- ]{8,20}$"))
					throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다: " + destination);
			}
			case PUSH -> {
				// 기기 토큰: 형식 제한 없음
			}
		}
	}

	private CustomerNotification lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("알림", id));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
