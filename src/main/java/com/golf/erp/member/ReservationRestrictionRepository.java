package com.golf.erp.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ReservationRestrictionRepository
		extends JpaRepository<ReservationRestriction, Long>, JpaSpecificationExecutor<ReservationRestriction> {
	List<ReservationRestriction> findByMemberIdOrderByStartsAtDescIdDesc(Long memberId);

	Optional<ReservationRestriction> findByIdAndMemberId(Long id, Long memberId);
}
