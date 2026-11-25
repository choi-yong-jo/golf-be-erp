package com.golf.erp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** 업무 시각 기준은 Asia/Seoul. 서비스는 LocalDateTime.now(clock)으로 현재 시각을 얻는다. */
@Configuration
public class TimeConfig {
	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}
}
