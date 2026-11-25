package com.golf.erp.redis;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 이 서버에 연결된 예약 현황 화면(SSE)들. courseId 를 정해 구독하면 그 코스 이벤트만 받는다. */
@Component
public class TeeTimeSseHub {
	private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

	/** emitter → 구독 코스 (null 이면 전체) */
	private final Map<SseEmitter, Long> subscribers = new ConcurrentHashMap<>();
	private final Map<SseEmitter, Boolean> allCourses = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long golfCourseId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
		if (golfCourseId == null)
			allCourses.put(emitter, Boolean.TRUE);
		else
			subscribers.put(emitter, golfCourseId);
		emitter.onCompletion(() -> remove(emitter));
		emitter.onTimeout(() -> remove(emitter));
		emitter.onError(e -> remove(emitter));
		send(emitter, SseEmitter.event().name("connected").data("ok"));
		return emitter;
	}

	public void broadcast(TeeTimeEvent event) {
		allCourses.keySet().forEach(e -> send(e, SseEmitter.event().name("tee-time").data(event)));
		subscribers.forEach((e, courseId) -> {
			if (event.golfCourseId() == null || courseId.equals(event.golfCourseId()))
				send(e, SseEmitter.event().name("tee-time").data(event));
		});
	}

	public int size() {
		return subscribers.size() + allCourses.size();
	}

	/** 프록시가 유휴 연결을 끊지 않도록 25초마다 주석 이벤트를 보낸다. */
	@Scheduled(fixedRate = 25_000)
	void heartbeat() {
		allCourses.keySet().forEach(e -> send(e, SseEmitter.event().comment("ping")));
		subscribers.keySet().forEach(e -> send(e, SseEmitter.event().comment("ping")));
	}

	private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
		try {
			emitter.send(event);
		} catch (IOException | IllegalStateException e) {
			remove(emitter);
		}
	}

	private void remove(SseEmitter emitter) {
		subscribers.remove(emitter);
		allCourses.remove(emitter);
	}
}
