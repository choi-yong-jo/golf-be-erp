package com.golf.erp.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis 채널을 구독해 받은 티타임 이벤트(ERP·예약 앱)를 예약 현황 화면(SSE)에 전달한다.
 * 기동 시 Redis 에 붙지 못해도 앱은 정상 기동하고, 30초마다 다시 구독을 시도한다.
 * 구독된 뒤 연결이 끊기면 리스너 컨테이너가 스스로 재연결한다.
 */
@Component
public class TeeTimeEventSubscriber implements DisposableBean {
	private static final Logger log = LoggerFactory.getLogger(TeeTimeEventSubscriber.class);

	private final RedisConnectionFactory connectionFactory;
	private final ErpRedisProperties props;
	private final ObjectMapper objectMapper;
	private final TeeTimeSseHub hub;
	private volatile RedisMessageListenerContainer container;
	private volatile boolean warned;

	public TeeTimeEventSubscriber(ObjectProvider<RedisConnectionFactory> connectionFactory, ErpRedisProperties props,
			ObjectMapper objectMapper, TeeTimeSseHub hub) {
		this.connectionFactory = props.enabled() ? connectionFactory.getIfAvailable() : null;
		this.props = props;
		this.objectMapper = objectMapper;
		this.hub = hub;
	}

	public boolean isSubscribed() {
		RedisMessageListenerContainer c = container;
		return c != null && c.isRunning() && c.isListening();
	}

	@Scheduled(initialDelay = 1_000, fixedDelay = 30_000)
	synchronized void ensureSubscribed() {
		if (connectionFactory == null || container != null)
			return;
		RedisMessageListenerContainer c = new RedisMessageListenerContainer();
		c.setConnectionFactory(connectionFactory);
		c.addMessageListener((message, pattern) -> onMessage(new String(message.getBody(), StandardCharsets.UTF_8)),
				new ChannelTopic(props.eventChannel()));
		try {
			c.afterPropertiesSet();
			c.start();
			container = c;
			warned = false;
			log.info("Redis 채널 구독 시작: {}", props.eventChannel());
		} catch (RuntimeException e) {
			destroyQuietly(c);
			if (!warned) {
				log.warn("Redis 채널 구독 실패, 30초마다 다시 시도합니다(실시간 알림은 이 서버 안에서만 전달): {}", e.toString());
				warned = true;
			}
		}
	}

	private void onMessage(String body) {
		try {
			hub.broadcast(objectMapper.readValue(body, TeeTimeEvent.class));
		} catch (Exception e) {
			log.warn("티타임 이벤트 처리 실패: {} - {}", body, e.toString());
		}
	}

	@Override
	public void destroy() {
		RedisMessageListenerContainer c = container;
		if (c != null)
			destroyQuietly(c);
	}

	private static void destroyQuietly(RedisMessageListenerContainer c) {
		try {
			c.destroy();
		} catch (Exception ignored) {
			// 종료 중 오류는 무시
		}
	}
}
