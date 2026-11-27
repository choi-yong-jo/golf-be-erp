package com.golf.erp.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StoreBrandRepository extends JpaRepository<StoreBrand, Long>, JpaSpecificationExecutor<StoreBrand> {
	boolean existsByBrandCode(String brandCode);

	/**
	 * 브랜드 사용 중지와 매장 영업 시작(등록·ACTIVE 전환·브랜드 변경)이 동시에 일어나 "사용 중지 브랜드의 영업 중 매장"이
	 * 생기지 않도록 브랜드 행을 잠그고 읽는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from StoreBrand b where b.id = :id")
	Optional<StoreBrand> findByIdForUpdate(@Param("id") Long id);
}
