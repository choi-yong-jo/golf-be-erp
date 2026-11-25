package com.golf.erp.member;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {
	boolean existsByMemberNo(String memberNo);

	boolean existsByMemberNoAndMemberIdNot(String memberNo, Long memberId);

	boolean existsByEmail(String email);

	boolean existsByEmailAndMemberIdNot(String email, Long memberId);

	/** 예약 앱과 같은 방식으로 회원 행을 잠가 한 회원의 예약 처리를 직렬화한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("select m from Member m where m.memberId = :id")
	Optional<Member> findByIdForUpdate(Long id);
}
