package com.golf.erp.rental;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RentalEquipmentStockRepository extends JpaRepository<RentalEquipmentStock, Long> {

	/** 수량 변경 전 행 잠금(SELECT ... FOR UPDATE) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from RentalEquipmentStock s where s.rentalEquipmentId = :equipmentId and s.golfCourseId = :golfCourseId")
	Optional<RentalEquipmentStock> findForUpdate(@Param("equipmentId") long equipmentId,
			@Param("golfCourseId") long golfCourseId);

	Optional<RentalEquipmentStock> findByRentalEquipmentIdAndGolfCourseId(Long rentalEquipmentId, Long golfCourseId);

	List<RentalEquipmentStock> findByGolfCourseId(Long golfCourseId);

	List<RentalEquipmentStock> findByRentalEquipmentIdOrderByGolfCourseId(Long rentalEquipmentId);

	/** 첫 입고 때 0 수량 행을 만든다. 동시에 두 요청이 와도 하나만 생기도록 ON CONFLICT DO NOTHING */
	@Modifying
	@Query(value = "INSERT INTO golf.rental_equipment_stock (rental_equipment_id, golf_course_id, total_quantity,"
			+ " available_quantity, rented_quantity, maintenance_quantity, updated_at)"
			+ " VALUES (:equipmentId, :golfCourseId, 0, 0, 0, 0, :now)"
			+ " ON CONFLICT (rental_equipment_id, golf_course_id) DO NOTHING", nativeQuery = true)
	int insertEmptyIfAbsent(@Param("equipmentId") long equipmentId, @Param("golfCourseId") long golfCourseId,
			@Param("now") LocalDateTime now);
}
