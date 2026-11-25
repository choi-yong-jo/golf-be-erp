package com.golf.erp.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Redis 연동 설정 (golf.redis).
 *
 * @param enabled               false 면 Redis 를 쓰지 않는다
 * @param appKeyPrefix          예약 앱 키 접두어 (예약 앱 app.redis.key-prefix 와 동일해야 함)
 * @param eventChannel          티타임/예약 변경 알림 Pub/Sub 채널
 * @param accessTokenTtl        예약 앱 access token 유효시간
 * @param revokeTokensOnSuspend 회원 정지/탈퇴 시 예약 앱 토큰 무효화 여부
 */
@ConfigurationProperties("golf.redis")
public record ErpRedisProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("golf-api:") String appKeyPrefix, @DefaultValue("golf:tee-time-events") String eventChannel,
		@DefaultValue("PT2H") Duration accessTokenTtl, @DefaultValue("true") boolean revokeTokensOnSuspend) {
}
