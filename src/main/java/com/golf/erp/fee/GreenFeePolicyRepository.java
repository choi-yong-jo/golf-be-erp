package com.golf.erp.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface GreenFeePolicyRepository
		extends JpaRepository<GreenFeePolicy, Long>, JpaSpecificationExecutor<GreenFeePolicy> {

	/** 해당 골프장(또는 공통) 활성 정책 중 유효기간이 [from, to] 와 겹치는 것 */
	@Query("""
			select p from GreenFeePolicy p
			where p.active = true and (p.golfCourseId = :golfCourseId or p.golfCourseId is null)
			  and p.validFrom <= :to and p.validTo >= :from""")
	List<GreenFeePolicy> findCandidates(Long golfCourseId, LocalDate from, LocalDate to);
}
