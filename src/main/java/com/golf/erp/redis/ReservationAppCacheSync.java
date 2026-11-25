package com.golf.erp.redis;

import com.golf.erp.member.MemberStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 예약 앱(golf-reservation-api)의 Redis 캐시를 ERP 변경 직후 지운다. 지우지 못해도 예약 앱 캐시는 30초 뒤 만료된다.
 * 키 형식은 예약 앱 코드와 같아야 한다.
 * <ul>
 * <li>티타임 목록: {prefix}tee-times:{yyyy-MM-dd}:all / {prefix}tee-times:{yyyy-MM-dd}:{golfCourseId}
 * (TeeTimeViewCache)</li>
 * <li>회원 상태: {prefix}auth:member-status:{memberId} (MemberStatusChecker)</li>
 * <li>토큰 무효화: {prefix}auth:revoked-before:{memberId} = epoch 초 (RedisTokenRevocationStore)</li>
 * </ul>
 */
@Component
public class ReservationAppCacheSync {
	private final RedisTasks tasks;
	private final ErpRedisProperties props;
	private final Clock clock;

	public ReservationAppCacheSync(RedisTasks tasks, ErpRedisProperties props, Clock clock) {
		this.tasks = tasks;
		this.props = props;
		this.clock = clock;
	}

	/** 해당 날짜들의 고객용 티타임 목록 캐시(전체 + 코스별)를 커밋 후 지운다. */
	public void evictTeeTimes(Long golfCourseId, Collection<LocalDate> dates) {
		if (!tasks.enabled() || dates.isEmpty())
			return;
		List<String> keys = new ArrayList<>();
		for (LocalDate d : dates) {
			keys.add(teeTimeKey(d, null));
			if (golfCourseId != null)
				keys.add(teeTimeKey(d, golfCourseId));
		}
		tasks.afterCommit("예약 앱 티타임 캐시 삭제 " + keys, redis -> redis.delete(keys), null);
	}

	/**
	 * 회원 상태 캐시를 지워 예약 앱이 DB에서 다시 읽게 한다. 정지/탈퇴면 지금까지 발급된 토큰도 무효화해 즉시 로그아웃시킨다.
	 */
	public void memberStatusChanged(long memberId, MemberStatus status) {
		if (!tasks.enabled())
			return;
		String statusKey = props.appKeyPrefix() + "auth:member-status:" + memberId;
		boolean revoke = status != MemberStatus.ACTIVE && props.revokeTokensOnSuspend();
		tasks.afterCommit("예약 앱 회원 상태 캐시 갱신 member=" + memberId, redis -> {
			redis.delete(statusKey);
			if (revoke)
				redis.opsForValue().set(props.appKeyPrefix() + "auth:revoked-before:" + memberId,
						String.valueOf(clock.instant().getEpochSecond()),
						props.accessTokenTtl().plus(Duration.ofMinutes(1)));
		}, null);
	}

	private String teeTimeKey(LocalDate date, Long golfCourseId) {
		return props.appKeyPrefix() + "tee-times:" + date + ":" + (golfCourseId == null ? "all" : golfCourseId);
	}
}
