package com.golf.erp.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface DailySettlementRepository
		extends JpaRepository<DailySettlement, Long>, JpaSpecificationExecutor<DailySettlement> {

	Optional<DailySettlement> findByGolfCourseIdAndBusinessDate(Long golfCourseId, LocalDate businessDate);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from DailySettlement s where s.id = :id")
	Optional<DailySettlement> findByIdForUpdate(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from DailySettlement s where s.golfCourseId = :golfCourseId and s.businessDate = :businessDate")
	Optional<DailySettlement> findForUpdate(Long golfCourseId, LocalDate businessDate);

	boolean existsByGolfCourseIdAndBusinessDateAndStatus(Long golfCourseId, LocalDate businessDate,
			DailySettlementStatus status);
}
