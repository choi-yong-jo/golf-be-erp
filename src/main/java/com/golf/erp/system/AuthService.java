package com.golf.erp.system;

import com.golf.erp.common.UnauthorizedException;
import com.golf.erp.security.AdminPrincipal;
import com.golf.erp.security.CurrentUser;
import com.golf.erp.security.ErpModule;
import com.golf.erp.security.PrincipalLoader;
import com.golf.erp.security.TokenService;
import com.golf.erp.system.SystemDtos.Me;
import com.golf.erp.system.SystemDtos.TokenResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {
	private static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다.";

	private final AdminUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokens;
	private final PrincipalLoader principalLoader;
	private final Clock clock;
	/** 없는 아이디도 비밀번호 비교 시간을 같게 해서 아이디 존재 여부가 드러나지 않게 한다. */
	private final String dummyHash;

	public AuthService(AdminUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
			PrincipalLoader principalLoader, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.principalLoader = principalLoader;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	public TokenResponse login(String username, String password) {
		AdminUser user = users.findByUsername(username.trim()).orElse(null);
		if (user == null) {
			passwordEncoder.matches(password, dummyHash);
			throw new UnauthorizedException(LOGIN_FAILED);
		}
		if (!passwordEncoder.matches(password, user.getPasswordHash()))
			throw new UnauthorizedException(LOGIN_FAILED);
		if (!user.isActive())
			throw new UnauthorizedException("비활성화된 계정입니다. 관리자에게 문의하세요.");
		user.loggedIn(LocalDateTime.now(clock));
		return issue(user.getId());
	}

	public TokenResponse refresh(String refreshToken) {
		long id;
		try {
			id = tokens.verifyRefresh(refreshToken);
		} catch (IllegalArgumentException e) {
			throw new UnauthorizedException(e.getMessage());
		}
		return issue(id);
	}

	@Transactional(readOnly = true)
	public Me me() {
		return toMe(CurrentUser.require());
	}

	public void changePassword(String currentPassword, String newPassword) {
		AdminUser user = users.findById(CurrentUser.require().id())
				.orElseThrow(() -> new UnauthorizedException("계정을 찾을 수 없습니다."));
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash()))
			throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
		user.changePassword(passwordEncoder.encode(newPassword), LocalDateTime.now(clock));
	}

	private TokenResponse issue(long adminUserId) {
		AdminPrincipal p = principalLoader.loadActive(adminUserId)
				.orElseThrow(() -> new UnauthorizedException("비활성화되었거나 없는 계정입니다."));
		TokenService.Tokens t = tokens.issue(p.id(), p.username());
		return new TokenResponse("Bearer", t.accessToken(), t.accessTokenExpiresIn(), t.refreshToken(),
				t.refreshTokenExpiresIn(), toMe(p));
	}

	/** ADMIN 은 모든 모듈 권한을 가진 것으로 보여준다(화면 제어용). */
	static Me toMe(AdminPrincipal p) {
		Set<String> permissions = p.isAdmin()
				? Arrays.stream(ErpModule.values()).flatMap(m -> java.util.stream.Stream.of(m.readAuthority(),
						m.writeAuthority())).collect(Collectors.toCollection(TreeSet::new))
				: new TreeSet<>(p.permissions());
		return new Me(p.id(), p.username(), p.displayName(), p.role(), p.isAdmin(), permissions);
	}
}
