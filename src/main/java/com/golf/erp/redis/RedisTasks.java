package com.golf.erp.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Consumer;

/**
 * Redis 작업 실행기. 트랜잭션 안에서 부르면 커밋된 뒤에, 별도 스레드에서, 실패해도 예외 없이(로그만) 실행한다.
 * 롤백되면 실행하지 않는다.
 */
@Component
public class RedisTasks {
	private static final Logger log = LoggerFactory.getLogger(RedisTasks.class);

	private final StringRedisTemplate redis;
	private final ThreadPoolTaskExecutor executor;

	public RedisTasks(ObjectProvider<StringRedisTemplate> redis, ErpRedisProperties props,
			@Qualifier("redisTaskExecutor") ThreadPoolTaskExecutor executor) {
		this.redis = props.enabled() ? redis.getIfAvailable() : null;
		this.executor = executor;
	}

	public boolean enabled() {
		return redis != null;
	}

	/**
	 * @param description 실패 로그에 남길 작업 설명
	 * @param task        Redis 작업
	 * @param onFailure   실패 시 대신 할 일 (없으면 null)
	 */
	public void afterCommit(String description, Consumer<StringRedisTemplate> task, Runnable onFailure) {
		Runnable run = () -> executor.execute(() -> {
			if (redis == null) {
				if (onFailure != null)
					onFailure.run();
				return;
			}
			try {
				task.accept(redis);
			} catch (RuntimeException e) {
				log.warn("Redis 작업 실패(ERP 업무에는 영향 없음): {} - {}", description, e.toString());
				if (onFailure != null)
					onFailure.run();
			}
		});
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					run.run();
				}
			});
		} else {
			run.run();
		}
	}
}
