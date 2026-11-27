package com.golf.erp.equipment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {
	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

	/** 정비 등록·종료 때 장비 상태를 함께 바꾸므로 행을 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Equipment e where e.id = :id")
	Optional<Equipment> findByIdForUpdate(@Param("id") Long id);
}
