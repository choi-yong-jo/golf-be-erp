package com.golf.erp.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface StoreMenuCategoryRepository
		extends JpaRepository<StoreMenuCategory, Long>, JpaSpecificationExecutor<StoreMenuCategory> {
	boolean existsByStoreIdAndCategoryName(Long storeId, String categoryName);

	boolean existsByStoreIdAndCategoryNameAndIdNot(Long storeId, String categoryName, Long id);

	Optional<StoreMenuCategory> findByIdAndStoreId(Long id, Long storeId);

	List<StoreMenuCategory> findByStoreId(Long storeId);
}
