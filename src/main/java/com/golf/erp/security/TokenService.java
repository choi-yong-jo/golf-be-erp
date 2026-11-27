package com.golf.erp.security;

import com.golf.erp.config.ErpProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * JWT(HS256) 발급·검증. sub = admin_user_id, typ = access | refresh.
 * 토큰에는 권한을 넣지 않는다(요청마다 DB 에서 읽음 → PrincipalLoader).
 */
@Component
public class TokenService {
	public static final String TYPE_CLAIM = "typ";
	public static final String ACCESS = "access";
	public static final String REFRESH = "refresh";

	private final JwtEncoder encoder;
	private final JwtDecoder decoder;
	private final ErpProperties.Jwt props;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, JwtDecoder decoder, ErpProperties props, Clock clock) {
		this.encoder = encoder;
		this.decoder = decoder;
		this.props = props.jwt();
		this.clock = clock;
	}

	public Tokens issue(long adminUserId, String username) {
		Instant now = clock.instant();
		return new Tokens(encode(adminUserId, username, ACCESS, now, now.plus(props.accessTokenTtl())),
				props.accessTokenTtl().toSeconds(),
				encode(adminUserId, username, REFRESH, now, now.plus(props.refreshTokenTtl())),
				props.refreshTokenTtl().toSeconds());
	}

	/** refresh 토큰을 검증하고 admin_user_id 를 돌려준다. 잘못되었거나 만료되면 IllegalArgumentException */
	public long verifyRefresh(String refreshToken) {
		try {
			Jwt jwt = decoder.decode(refreshToken);
			if (!REFRESH.equals(jwt.getClaimAsString(TYPE_CLAIM)))
				throw new IllegalArgumentException("refresh 토큰이 아닙니다.");
			return Long.parseLong(jwt.getSubject());
		} catch (JwtException | NumberFormatException e) {
			throw new IllegalArgumentException("refresh 토큰이 유효하지 않거나 만료되었습니다.");
		}
	}

	private String encode(long adminUserId, String username, String type, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(props.issuer()).subject(Long.toString(adminUserId))
				.issuedAt(issuedAt).expiresAt(expiresAt).id(UUID.randomUUID().toString()).claim(TYPE_CLAIM, type)
				.claim("username", username).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	public record Tokens(String accessToken, long accessTokenExpiresIn, String refreshToken,
			long refreshTokenExpiresIn) {
	}
}
