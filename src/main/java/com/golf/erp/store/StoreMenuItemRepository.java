package com.golf.erp.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreMenuItemRepository
		extends JpaRepository<StoreMenuItem, Long>, JpaSpecificationExecutor<StoreMenuItem> {
	boolean existsByStoreIdAndMenuCode(Long storeId, String menuCode);

	Optional<StoreMenuItem> findByIdAndStoreId(Long id, Long storeId);

	List<StoreMenuItem> findByStoreIdAndAvailableTrue(Long storeId);

	/** 분류별 메뉴 수. 행: [storeMenuCategoryId(Long), count(Long)] */
	@Query("select m.storeMenuCategoryId, count(m) from StoreMenuItem m where m.storeId = :storeId"
			+ " and m.storeMenuCategoryId is not null group by m.storeMenuCategoryId")
	List<Object[]> countByCategory(@Param("storeId") Long storeId);
}
