package com.golf.erp.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Redis 는 부가 기능이다. 장애가 나도 ERP 요청이 기다리거나 실패하지 않도록:
 * <ul>
 * <li>Redis 작업은 커밋 후 별도 스레드(redisTaskExecutor)에서 한다.</li>
 * <li>연결이 끊긴 동안의 명령은 쌓아 두지 않고 바로 실패시킨다(REJECT_COMMANDS).</li>
 * <li>작업 대기열이 넘치면 오래된 작업부터 버린다(예약 앱 캐시는 30초 뒤 어차피 만료된다).</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ErpRedisProperties.class)
public class RedisConfig {

	@Bean
	LettuceClientConfigurationBuilderCustomizer erpLettuceCustomizer() {
		return builder -> builder.clientOptions(ClientOptions.builder()
				.autoReconnect(true)
				.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
				.timeoutOptions(TimeoutOptions.enabled())
				.build());
	}

	@Bean
	ThreadPoolTaskExecutor redisTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(1000);
		executor.setThreadNamePrefix("redis-sync-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(false);
		return executor;
	}
}
