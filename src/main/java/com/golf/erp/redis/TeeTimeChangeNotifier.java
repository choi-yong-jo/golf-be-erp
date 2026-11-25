package com.golf.erp.redis;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 티타임/예약이 바뀌었을 때 ERP 서비스가 부르는 단일 진입점.
 * 예약 앱의 고객용 티타임 캐시를 지우고(1), 예약 현황 화면에 실시간 알림을 보낸다(3). 모두 커밋 후 비동기로 실행된다.
 */
@Component
public class TeeTimeChangeNotifier {
	private final ReservationAppCacheSync cacheSync;
	private final TeeTimeEventPublisher publisher;

	public TeeTimeChangeNotifier(ReservationAppCacheSync cacheSync, TeeTimeEventPublisher publisher) {
		this.cacheSync = cacheSync;
		this.publisher = publisher;
	}

	/** 티타임 상태가 바뀌는 변경 (고객 화면 캐시도 지운다) */
	public void teeTimeChanged(String type, Long golfCourseId, LocalDate playDate, Long teeTimeId,
			Long reservationId, String status) {
		if (playDate != null)
			cacheSync.evictTeeTimes(golfCourseId, List.of(playDate));
		publisher.publish(type, golfCourseId, playDate, teeTimeId, reservationId, status);
	}

	/** 일괄 생성처럼 여러 날짜가 바뀐 경우 */
	public void teeTimesGenerated(Long golfCourseId, Collection<LocalDate> dates) {
		cacheSync.evictTeeTimes(golfCourseId, dates);
		for (LocalDate d : dates)
			publisher.publish("TEE_TIME_CREATED", golfCourseId, d, null, null, "OPEN");
	}

	/** 티타임 상태는 그대로인 예약 변경 (확정·결제·완료·노쇼) - 고객 화면 캐시는 그대로 둔다 */
	public void reservationUpdated(Long golfCourseId, LocalDate playDate, Long teeTimeId, Long reservationId,
			String status) {
		publisher.publish("RESERVATION_UPDATED", golfCourseId, playDate, teeTimeId, reservationId, status);
	}
}
