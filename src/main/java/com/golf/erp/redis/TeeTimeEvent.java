package com.golf.erp.redis;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 티타임/예약 변경 알림. Redis 채널(golf.redis.event-channel)에 JSON 으로 오가며 ERP 와 예약 앱이 함께 쓰는 약속이다.
 * 받는 쪽은 golfCourseId·playDate 에 해당하는 화면을 다시 조회하면 된다.
 *
 * @param source        ERP 또는 APP(예약 앱)
 * @param type          TEE_TIME_CREATED, TEE_TIME_UPDATED, TEE_TIME_DELETED, RESERVATION_CREATED,
 *                      RESERVATION_UPDATED, RESERVATION_CANCELLED
 * @param golfCourseId  코스 (없을 수 있음)
 * @param playDate      플레이 날짜
 * @param teeTimeId     티타임 (없을 수 있음)
 * @param reservationId 예약 (예약 이벤트일 때)
 * @param status        바뀐 뒤 상태 (티타임 또는 예약 상태)
 * @param occurredAt    발생 시각
 */
public record TeeTimeEvent(String source, String type, Long golfCourseId, LocalDate playDate, Long teeTimeId,
		Long reservationId, String status, Instant occurredAt) {
}
