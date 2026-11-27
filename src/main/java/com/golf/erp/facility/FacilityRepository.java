package com.golf.erp.facility;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FacilityRepository extends JpaRepository<Facility, Long>, JpaSpecificationExecutor<Facility> {
	boolean existsByGolfCourseIdAndCode(Long golfCourseId, String code);

	boolean existsByGolfCourseIdAndCodeAndIdNot(Long golfCourseId, String code, Long id);

	/** 비활성화·유형 변경 전 시설 행 잠금 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Facility f where f.id = :id")
	Optional<Facility> findByIdForUpdate(@Param("id") long id);
}
