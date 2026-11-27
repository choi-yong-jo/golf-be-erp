package com.golf.erp.rental;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EquipmentRentalRepository
		extends JpaRepository<EquipmentRental, Long>, JpaSpecificationExecutor<EquipmentRental> {

	/** 상태·재고를 바꾸는 처리(출고·반납·취소·결제)는 렌탈 행을 먼저 잠가 같은 렌탈의 동시 처리를 막는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from EquipmentRental r where r.id = :id")
	Optional<EquipmentRental> findByIdForUpdate(@Param("id") long id);

	/** 기한이 지난 대여 중 렌탈(전체 골프장) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from EquipmentRental r where r.status in :statuses and r.dueAt < :now order by r.id")
	List<EquipmentRental> findPastDueForUpdate(@Param("statuses") Collection<RentalStatus> statuses,
			@Param("now") LocalDateTime now);

	/** 기한이 지난 대여 중 렌탈(한 골프장) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from EquipmentRental r where r.status in :statuses and r.dueAt < :now"
			+ " and r.golfCourseId = :golfCourseId order by r.id")
	List<EquipmentRental> findPastDueForUpdate(@Param("statuses") Collection<RentalStatus> statuses,
			@Param("now") LocalDateTime now, @Param("golfCourseId") long golfCourseId);
}
