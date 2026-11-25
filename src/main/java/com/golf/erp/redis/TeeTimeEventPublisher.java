package com.golf.erp.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * ERP 변경을 커밋 후 Redis 채널로 알린다. 구독 중인 모든 ERP 서버(와 예약 앱)가 받는다.
 * Redis 가 없거나 실패하면 이 서버의 화면에만 직접 전달한다.
 */
@Component
public class TeeTimeEventPublisher {
	private final RedisTasks tasks;
	private final ErpRedisProperties props;
	private final ObjectMapper objectMapper;
	private final TeeTimeSseHub hub;
	private final TeeTimeEventSubscriber subscriber;
	private final Clock clock;

	public TeeTimeEventPublisher(RedisTasks tasks, ErpRedisProperties props, ObjectMapper objectMapper,
			TeeTimeSseHub hub, TeeTimeEventSubscriber subscriber, Clock clock) {
		this.tasks = tasks;
		this.props = props;
		this.objectMapper = objectMapper;
		this.hub = hub;
		this.subscriber = subscriber;
		this.clock = clock;
	}

	public void publish(String type, Long golfCourseId, LocalDate playDate, Long teeTimeId, Long reservationId,
			String status) {
		TeeTimeEvent event = new TeeTimeEvent("ERP", type, golfCourseId, playDate, teeTimeId, reservationId, status,
				clock.instant());
		String json;
		try {
			json = objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
		tasks.afterCommit("티타임 이벤트 발행 " + type, redis -> {
			if (subscriber.isSubscribed()) {
				// 이 서버 화면에는 구독을 거쳐 전달된다. 발행이 실패하면 onFailure 가 직접 전달한다.
				redis.convertAndSend(props.eventChannel(), json);
				return;
			}
			// 구독이 아직/잠시 안 된 상태: 이 서버 화면에는 직접 전달하고, 다른 서버를 위해 발행은 시도만 한다
			hub.broadcast(event);
			try {
				redis.convertAndSend(props.eventChannel(), json);
			} catch (RuntimeException ignored) {
				// 이미 이 서버 화면에는 전달했다
			}
		}, () -> hub.broadcast(event));
	}
}
