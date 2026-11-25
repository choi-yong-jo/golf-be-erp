package com.golf.erp.fee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface GreenFeePolicyRepository extends JpaRepository<GreenFeePolicy, Long> {

	@Query("""
			select p from GreenFeePolicy p
			where (:courseId is null or p.golfCourseId = :courseId or p.golfCourseId is null)
			  and (:activeOnly = false or p.active = true)
			order by p.golfCourseId nulls first, p.validFrom desc, p.greenFeePolicyId desc""")
	List<GreenFeePolicy> search(Long courseId, boolean activeOnly);

	/** 해당 코스(또는 공통) 정책 중 date 가 유효기간에 포함되는 활성 정책 */
	@Query("""
			select p from GreenFeePolicy p
			where p.active = true and (p.golfCourseId = :courseId or p.golfCourseId is null)
			  and p.validFrom <= :date and p.validTo >= :date""")
	List<GreenFeePolicy> findCandidates(Long courseId, LocalDate date);
}
