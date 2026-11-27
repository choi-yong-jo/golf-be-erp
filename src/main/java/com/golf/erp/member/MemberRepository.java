package com.golf.erp.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {
	boolean existsByMemberNo(String memberNo);

	boolean existsByMemberNoAndIdNot(String memberNo, Long id);

	boolean existsByEmail(String email);

	boolean existsByEmailAndIdNot(String email, Long id);
}
