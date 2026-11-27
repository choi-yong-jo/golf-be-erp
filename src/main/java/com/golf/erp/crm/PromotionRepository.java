package com.golf.erp.crm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long>, JpaSpecificationExecutor<Promotion> {
	Optional<Promotion> findByCode(String code);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

	/** 사용 등록 시 같은 예약 중복 사용 검사를 직렬화하려고 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Promotion p where p.id = :id")
	Optional<Promotion> findByIdForUpdate(@Param("id") long id);
}
