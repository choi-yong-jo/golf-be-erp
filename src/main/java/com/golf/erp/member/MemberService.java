package com.golf.erp.member;

import com.golf.erp.member.dto.MemberRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class MemberService {
	private final MemberRepository repo;

	public MemberService(MemberRepository repo) {
		this.repo = repo;
	}

	@Transactional(readOnly = true)
	public List<Member> findAll() {
		return repo.findAll();
	}

	@Transactional(readOnly = true)
	public Member findById(long id) {
		return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다: " + id));
	}

	public Member create(MemberRequest r) {
		if (repo.existsByMemberNo(r.memberNo()))
			throw new IllegalArgumentException("이미 사용 중인 회원번호입니다.");
		return repo.save(new Member(r.memberNo(), r.name(), r.phone(), r.email()));
	}

	public Member update(long id, MemberRequest r) {
		Member m = findById(id);
		if (!m.getMemberNo().equals(r.memberNo()) && repo.existsByMemberNo(r.memberNo()))
			throw new IllegalArgumentException("이미 사용 중인 회원번호입니다.");
		m.update(r.name(), r.phone(), r.email(), r.status() == null ? "ACTIVE" : r.status());
		return m;
	}

	public void delete(long id) {
		repo.delete(findById(id));
	}
}
