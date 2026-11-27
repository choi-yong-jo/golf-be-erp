package com.golf.erp.system;

import com.golf.erp.system.SystemDtos.ChangePasswordRequest;
import com.golf.erp.system.SystemDtos.LoginRequest;
import com.golf.erp.system.SystemDtos.Me;
import com.golf.erp.system.SystemDtos.RefreshRequest;
import com.golf.erp.system.SystemDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService service;

	public AuthController(AuthService service) {
		this.service = service;
	}

	@Operation(summary = "로그인", description = "accessToken(기본 2시간)과 refreshToken(기본 14일)을 발급한다.")
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest r) {
		return service.login(r.username(), r.password());
	}

	@Operation(summary = "토큰 재발급", description = "refreshToken 으로 새 토큰 쌍을 받는다.")
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest r) {
		return service.refresh(r.refreshToken());
	}

	@Operation(summary = "내 정보와 권한")
	@GetMapping("/me")
	public Me me() {
		return service.me();
	}

	@Operation(summary = "내 비밀번호 변경")
	@PostMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest r) {
		service.changePassword(r.currentPassword(), r.newPassword());
	}
}
