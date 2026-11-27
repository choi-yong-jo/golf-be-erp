package com.golf.erp.config;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: /swagger-ui.html. 우측 상단 Authorize 에 로그인 응답의 accessToken 을 넣는다. */
@Configuration
public class OpenApiConfig {
	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info().title("Golf ERP API").version("v1")
						.description("골프장 관리자(ERP) REST API. POST /api/auth/login 으로 받은 accessToken 을 Bearer 로 보낸다."))
				.components(new Components().addSecuritySchemes("bearer",
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList("bearer"));
	}

	/**
	 * 모듈마다 XxxDtos 안에 같은 이름의 record(PaymentResponse, PlayerRequest ...)가 있어 스키마가 덮어써지지 않도록
	 * "RoundDtos.PlayerRequest" → "RoundPlayerRequest" 처럼 모듈 이름을 붙인다.
	 */
	@Bean
	ModelResolver dtoAwareModelResolver() {
		return new ModelResolver(Json.mapper(), new DtoTypeNameResolver());
	}

	static class DtoTypeNameResolver extends TypeNameResolver {
		@Override
		protected String getNameOfClass(Class<?> cls) {
			Class<?> outer = cls.getEnclosingClass();
			if (outer != null && outer.getSimpleName().endsWith("Dtos")) {
				String module = outer.getSimpleName().substring(0, outer.getSimpleName().length() - "Dtos".length());
				String name = cls.getSimpleName();
				if (name.startsWith(module))
					return name;
				// "StoreMenuDtos.MenuItemRequest" → "StoreMenuItemRequest" (겹치는 마지막 단어는 한 번만)
				int lastWord = Math.max(0, findLastUpper(module));
				String tail = module.substring(lastWord);
				return name.startsWith(tail) ? module.substring(0, lastWord) + name : module + name;
			}
			return super.getNameOfClass(cls);
		}

		private static int findLastUpper(String s) {
			for (int i = s.length() - 1; i > 0; i--)
				if (Character.isUpperCase(s.charAt(i)))
					return i;
			return 0;
		}
	}
}
