package com.golf.erp.member;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.member.MemberDtos.MemberRequest;
import com.golf.erp.member.MemberDtos.MemberReservationRow;
import com.golf.erp.member.MemberDtos.MemberResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional
public class MemberService {
	private static final String MEMBER_NO_PREFIX = "MB";

	private final MemberRepository repo;
	private final MemberReservationQuery reservations;
	private final AuditLogger audit;
	private final Clock clock;

	public MemberService(MemberRepository repo, MemberReservationQuery reservations, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.reservations = reservations;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 이름, 회원번호, 전화, 이메일, 로그인 ID 부분 일치 */
	@Transactional(readOnly = true)
	public PageResult<MemberResponse> list(String keyword, MemberStatus status, String grade, int page, int size) {
		Specification<Member> spec = Specification.allOf(
				Specs.like(keyword, "name", "memberNo", "phone", "email", "loginId"), Specs.eq("status", status),
				Specs.eq("grade", normalizeGrade(grade)));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Direction.DESC, "id"))),
				MemberResponse::of);
	}

	@Transactional(readOnly = true)
	public MemberResponse get(long id) {
		return MemberResponse.of(find(id));
	}

	/** 오프라인 회원 등록(로그인 계정 없음). 회원번호를 비우면 저장 후 MB-yyyyMMdd-{id} 로 발급한다. */
	public MemberResponse create(MemberRequest r) {
		String memberNo = blankToNull(r.memberNo());
		String email = normalizeEmail(r.email());
		if (memberNo != null && repo.existsByMemberNo(memberNo))
			throw new ConflictException("이미 사용 중인 회원번호입니다: " + memberNo);
		if (email != null && repo.existsByEmail(email))
			throw new ConflictException("이미 사용 중인 이메일입니다: " + email);
		LocalDateTime now = now();
		Member m = new Member(now);
		apply(m, r, memberNo, email, now);
		repo.saveAndFlush(m);
		if (memberNo == null)
			m.assignMemberNo(DocumentNo.of(MEMBER_NO_PREFIX, now.toLocalDate(), m.getId()));
		audit.log("CREATE", "member", m.getId(), Map.of("memberNo", m.getMemberNo()));
		return MemberResponse.of(m);
	}

	/** 회원번호를 비우면 기존 번호를 유지한다. */
	public MemberResponse update(long id, MemberRequest r) {
		Member m = find(id);
		String memberNo = blankToNull(r.memberNo());
		if (memberNo == null)
			memberNo = m.getMemberNo();
		else if (repo.existsByMemberNoAndIdNot(memberNo, id))
			throw new ConflictException("이미 사용 중인 회원번호입니다: " + memberNo);
		String email = normalizeEmail(r.email());
		if (email != null && repo.existsByEmailAndIdNot(email, id))
			throw new ConflictException("이미 사용 중인 이메일입니다: " + email);
		apply(m, r, memberNo, email, now());
		return MemberResponse.of(m);
	}

	/** 삭제 대신 상태 변경(ACTIVE/SUSPENDED/WITHDRAWN). 이미 잡힌 예약은 그대로 두고, 이후 예약 생성만 막힌다. */
	public MemberResponse changeStatus(long id, MemberStatus status, String reason) {
		Member m = find(id);
		MemberStatus before = m.getStatus();
		if (before == status)
			return MemberResponse.of(m);
		m.changeStatus(status, now());
		Map<String, Object> details = new HashMap<>();
		details.put("before", before);
		details.put("after", status);
		if (blankToNull(reason) != null)
			details.put("reason", reason.trim());
		audit.log("STATUS_CHANGE", "member", id, details);
		return MemberResponse.of(m);
	}

	/** 회원의 예약 내역(플레이 일시 최근 순) */
	@Transactional(readOnly = true)
	public PageResult<MemberReservationRow> reservations(long id, String status, int page, int size) {
		Pages.of(page, size, Sort.unsorted());
		find(id);
		return reservations.find(id, blankToNull(status), page, size);
	}

	Member find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("회원", id));
	}

	private static void apply(Member m, MemberRequest r, String memberNo, String email, LocalDateTime now) {
		m.update(memberNo, r.name().trim(), blankToNull(r.phone()), email, r.birthDate(), blankToNull(r.gender()),
				blankToNull(r.address()), normalizeGrade(r.grade()), Boolean.TRUE.equals(r.marketingConsent()), now);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String normalizeEmail(String email) {
		String e = blankToNull(email);
		return e == null ? null : e.toLowerCase(Locale.ROOT);
	}

	private static String normalizeGrade(String grade) {
		String g = blankToNull(grade);
		return g == null ? null : g.toUpperCase(Locale.ROOT);
	}
}
