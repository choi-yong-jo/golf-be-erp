package com.golf.erp.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TenantStoreRepository extends JpaRepository<TenantStore, Long>, JpaSpecificationExecutor<TenantStore> {
	boolean existsByGolfCourseIdAndStoreCode(Long golfCourseId, String storeCode);

	long countByStoreBrandIdAndStatus(Long storeBrandId, StoreStatus status);

	/** 브랜드별·상태별 매장 수. 행: [storeBrandId(Long), status(StoreStatus), count(Long)] */
	@Query("select s.storeBrandId, s.status, count(s) from TenantStore s where s.storeBrandId in :brandIds"
			+ " group by s.storeBrandId, s.status")
	List<Object[]> countByBrandAndStatus(@Param("brandIds") Collection<Long> brandIds);
}
