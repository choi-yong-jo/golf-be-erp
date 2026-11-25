package com.golf.erp.member;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.member.dto.MemberRequest;
import com.golf.erp.redis.ReservationAppCacheSync;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional
public class MemberService {
	private final MemberRepository repo;
	private final ReservationAppCacheSync cacheSync;
	private final Clock clock;

	public MemberService(MemberRepository repo, ReservationAppCacheSync cacheSync, Clock clock) {
		this.repo = repo;
		this.cacheSync = cacheSync;
		this.clock = clock;
	}

	/** keyword: 이름, 회원번호, 전화, 이메일, 로그인ID 부분 일치 */
	@Transactional(readOnly = true)
	public PageResult<Member> search(String keyword, MemberStatus status, int page, int size) {
		PageResult.validate(page, size);
		Specification<Member> spec = (root, q, cb) -> cb.conjunction();
		if (keyword != null && !keyword.isBlank()) {
			String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
			spec = spec.and((root, q, cb) -> cb.or(cb.like(cb.lower(root.get("memberName")), like),
					cb.like(cb.lower(root.get("memberNo")), like), cb.like(root.get("phone"), like),
					cb.like(cb.lower(root.get("email")), like), cb.like(cb.lower(root.get("loginId")), like)));
		}
		if (status != null)
			spec = spec.and((root, q, cb) -> cb.equal(root.get("memberStatus"), status));
		Page<Member> result = repo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "memberId")));
		return new PageResult<>(result.getContent(), page, size, result.getTotalElements());
	}

	@Transactional(readOnly = true)
	public Member get(long id) {
		return repo.findById(id).orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다: " + id));
	}

	public Member create(MemberRequest r) {
		String memberNo = blankToNull(r.memberNo());
		String email = normalizeEmail(r.email());
		if (memberNo != null && repo.existsByMemberNo(memberNo))
			throw new ConflictException("이미 사용 중인 회원번호입니다.");
		if (email != null && repo.existsByEmail(email))
			throw new ConflictException("이미 사용 중인 이메일입니다.");
		return repo.save(new Member(memberNo, r.memberName().trim(), blankToNull(r.phone()), email, now()));
	}

	public Member update(long id, MemberRequest r) {
		Member m = get(id);
		String memberNo = blankToNull(r.memberNo());
		String email = normalizeEmail(r.email());
		if (memberNo != null && repo.existsByMemberNoAndMemberIdNot(memberNo, id))
			throw new ConflictException("이미 사용 중인 회원번호입니다.");
		if (email != null && repo.existsByEmailAndMemberIdNot(email, id))
			throw new ConflictException("이미 사용 중인 이메일입니다.");
		m.update(memberNo, r.memberName().trim(), blankToNull(r.phone()), email, now());
		return m;
	}

	/** 회원은 예약/결제 이력과 연결되어 있어 삭제하지 않고 상태(SUSPENDED/WITHDRAWN)로 관리한다. */
	public Member changeStatus(long id, MemberStatus status) {
		Member m = get(id);
		m.changeStatus(status, now());
		// 커밋 후 예약 앱의 회원 상태 캐시 삭제, 정지/탈퇴면 예약 앱 토큰도 무효화 (Redis 실패해도 무시)
		cacheSync.memberStatusChanged(id, status);
		return m;
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String normalizeEmail(String email) {
		String e = blankToNull(email);
		return e == null ? null : e.toLowerCase(Locale.ROOT);
	}
}
