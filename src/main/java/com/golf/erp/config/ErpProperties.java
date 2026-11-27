package com.golf.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** application.yml 의 erp.* */
@ConfigurationProperties(prefix = "erp")
public record ErpProperties(Jwt jwt, Bootstrap bootstrap, Cors cors, File file) {

	/** 업로드 파일 저장 위치(LOCAL 스토리지). 파일은 storageDir/{storage_key} 에 저장된다. */
	public record File(String storageDir) {
	}

	public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
	}

	public record Bootstrap(String adminUsername, String adminPassword) {
	}

	public record Cors(List<String> allowedOrigins) {
	}
}
