package com.golf.erp.member;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
	boolean existsByMemberNo(String memberNo);
}
