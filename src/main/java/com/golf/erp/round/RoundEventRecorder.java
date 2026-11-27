package com.golf.erp.round;

import com.golf.erp.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** 라운드 이용 내역(round_event) 기록. 호출한 서비스의 트랜잭션에 묶인다. 행위자는 현재 로그인한 직원. */
@Component
public class RoundEventRecorder {
	private static final int MAX_MESSAGE = 1000;

	private final RoundEventRepository repo;
	private final Clock clock;

	public RoundEventRecorder(RoundEventRepository repo, Clock clock) {
		this.repo = repo;
		this.clock = clock;
	}

	public void record(long roundId, RoundEventType type, String message) {
		record(roundId, type, null, null, message);
	}

	public void record(long roundId, RoundEventType type, Long courseId, Long roundPlayerId, String message) {
		String m = message != null && message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
		repo.save(new RoundEvent(roundId, type, courseId, roundPlayerId, m, CurrentUser.idOrNull(),
				LocalDateTime.now(clock)));
	}
}
