package com.golf.erp.crm;

/**
 * 알림 발송 상태(ck_notification_status). QUEUED 에서 SENT·FAILED·CANCELLED 로 간다.
 * FAILED 는 재시도(retry)로 다시 QUEUED 가 될 수 있다.
 */
public enum NotificationStatus {
	QUEUED, SENT, FAILED, CANCELLED
}
