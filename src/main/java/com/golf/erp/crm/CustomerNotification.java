package com.golf.erp.crm;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.customer_notification. 고객 알림 발송 대기열.
 * 이 서버는 실제로 발송하지 않는다. 외부 발송기(배치·워커)가 대기열(GET /api/notifications/queue)을 가져가
 * 보낸 뒤 결과를 mark-sent / mark-failed 로 알려 준다.
 */
@Entity
@Table(name = "customer_notification")
public class CustomerNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "notification_id")
	private Long id;
	@Column(name = "member_id")
	private Long memberId;
	@Column(name = "reservation_id")
	private Long reservationId;
	@Enumerated(EnumType.STRING)
	@Column(name = "channel", nullable = false, length = 20)
	private NotificationChannel channel;
	@Column(name = "template_code", length = 60)
	private String templateCode;
	@Column(name = "destination", nullable = false, length = 255)
	private String destination;
	@Column(name = "message", nullable = false, columnDefinition = "text")
	private String message;
	@Enumerated(EnumType.STRING)
	@Column(name = "send_status", nullable = false, length = 20)
	private NotificationStatus status = NotificationStatus.QUEUED;
	@Column(name = "scheduled_at")
	private LocalDateTime scheduledAt;
	@Column(name = "sent_at")
	private LocalDateTime sentAt;
	@Column(name = "provider_message_id", length = 150)
	private String providerMessageId;
	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	protected CustomerNotification() {
	}

	public CustomerNotification(Long memberId, Long reservationId, NotificationChannel channel, String templateCode,
			String destination, String message, LocalDateTime scheduledAt) {
		this.memberId = memberId;
		this.reservationId = reservationId;
		this.channel = channel;
		this.templateCode = templateCode;
		this.destination = destination;
		this.message = message;
		this.scheduledAt = scheduledAt;
	}

	public void markSent(String providerMessageId, LocalDateTime now) {
		requireQueued("발송 완료 처리");
		status = NotificationStatus.SENT;
		sentAt = now;
		this.providerMessageId = providerMessageId;
		errorMessage = null;
	}

	public void markFailed(String providerMessageId, String errorMessage) {
		requireQueued("발송 실패 처리");
		status = NotificationStatus.FAILED;
		if (providerMessageId != null)
			this.providerMessageId = providerMessageId;
		this.errorMessage = errorMessage;
	}

	public void cancel() {
		requireQueued("취소");
		status = NotificationStatus.CANCELLED;
	}

	/** 실패한 알림을 다시 대기열에 넣는다. 오류 메시지는 지운다. */
	public void retry(LocalDateTime scheduledAt) {
		if (status != NotificationStatus.FAILED)
			throw new ConflictException("발송 실패(FAILED)한 알림만 재시도할 수 있습니다. 현재: " + status);
		status = NotificationStatus.QUEUED;
		this.scheduledAt = scheduledAt;
		errorMessage = null;
	}

	private void requireQueued(String action) {
		if (status != NotificationStatus.QUEUED)
			throw new ConflictException("발송 대기(QUEUED) 중인 알림만 " + action + "할 수 있습니다. 현재: " + status);
	}

	public Long getId() {
		return id;
	}

	public Long getMemberId() {
		return memberId;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public NotificationChannel getChannel() {
		return channel;
	}

	public String getTemplateCode() {
		return templateCode;
	}

	public String getDestination() {
		return destination;
	}

	public String getMessage() {
		return message;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public LocalDateTime getScheduledAt() {
		return scheduledAt;
	}

	public LocalDateTime getSentAt() {
		return sentAt;
	}

	public String getProviderMessageId() {
		return providerMessageId;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
