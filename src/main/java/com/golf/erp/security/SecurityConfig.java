package com.golf.erp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.golf.erp.common.ApiExceptionHandler;
import com.golf.erp.config.ErpProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 인증: Authorization: Bearer {accessToken}. 세션 없음.
 * 인가: URL → 모듈(ErpModule) → {모듈}_READ / {모듈}_WRITE 권한. admin_user.role = ADMIN 은 전체 허용.
 */
@Configuration
public class SecurityConfig {
	private static final String[] PUBLIC = { "/api/auth/login", "/api/auth/refresh", "/swagger-ui.html",
			"/swagger-ui/**", "/v3/api-docs/**", "/actuator/health/**", "/actuator/info", "/error" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, PrincipalLoader principalLoader, ObjectMapper json)
			throws Exception {
		http.csrf(c -> c.disable()).cors(c -> {
		}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(b -> b.disable()).formLogin(f -> f.disable()).logout(l -> l.disable());

		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll().requestMatchers(PUBLIC).permitAll();
			for (ErpModule m : ErpModule.values()) {
				auth.requestMatchers(HttpMethod.GET, m.paths).hasAnyAuthority(m.readAuthority(), m.writeAuthority(),
						ErpModule.ADMIN_AUTHORITY);
				auth.requestMatchers(m.paths).hasAnyAuthority(m.writeAuthority(), ErpModule.ADMIN_AUTHORITY);
			}
			auth.anyRequest().authenticated();
		});

		http.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter(principalLoader)))
				.authenticationEntryPoint((req, res, e) -> write(res, json, HttpStatus.UNAUTHORIZED,
						"인증이 필요합니다. 토큰이 없거나 만료되었습니다."))
				.accessDeniedHandler((req, res, e) -> write(res, json, HttpStatus.FORBIDDEN, "권한이 없습니다.")));
		http.exceptionHandling(e -> e
				.authenticationEntryPoint((req, res, ex) -> write(res, json, HttpStatus.UNAUTHORIZED, "인증이 필요합니다."))
				.accessDeniedHandler((req, res, ex) -> write(res, json, HttpStatus.FORBIDDEN, "권한이 없습니다.")));
		return http.build();
	}

	/** access 토큰만 허용하고, 계정을 DB 에서 다시 읽어 비활성 계정을 막는다. */
	private static Converter<Jwt, AbstractAuthenticationToken> converter(PrincipalLoader loader) {
		return jwt -> {
			if (!TokenService.ACCESS.equals(jwt.getClaimAsString(TokenService.TYPE_CLAIM)))
				throw new InvalidBearerTokenException("access 토큰이 아닙니다.");
			long id;
			try {
				id = Long.parseLong(jwt.getSubject());
			} catch (NumberFormatException e) {
				throw new InvalidBearerTokenException("잘못된 토큰입니다.");
			}
			AdminPrincipal p = loader.loadActive(id)
					.orElseThrow(() -> new InvalidBearerTokenException("비활성화되었거나 없는 계정입니다."));
			List<GrantedAuthority> authorities = new ArrayList<>();
			authorities.add(new SimpleGrantedAuthority("ROLE_" + p.role()));
			p.permissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
			return UsernamePasswordAuthenticationToken.authenticated(p, jwt.getTokenValue(), authorities);
		};
	}

	private static void write(HttpServletResponse res, ObjectMapper json, HttpStatus status, String message)
			throws IOException {
		res.setStatus(status.value());
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		json.writeValue(res.getOutputStream(), ApiExceptionHandler.errorBody(status, message));
	}

	@Bean
	SecretKey jwtKey(ErpProperties props) {
		String secret = props.jwt().secret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
			throw new IllegalStateException("erp.jwt.secret(JWT_SECRET) 은 32바이트 이상이어야 합니다.");
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey, ErpProperties props) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(props.jwt().issuer())));
		return decoder;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(ErpProperties props) {
		CorsConfiguration c = new CorsConfiguration();
		c.setAllowedOrigins(props.cors().allowedOrigins());
		c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		c.setAllowedHeaders(List.of("*"));
		c.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", c);
		return source;
	}
}
