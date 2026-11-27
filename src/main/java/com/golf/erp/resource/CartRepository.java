package com.golf.erp.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CartRepository extends JpaRepository<Cart, Long>, JpaSpecificationExecutor<Cart> {
	boolean existsByGolfCourseIdAndCartNo(Long golfCourseId, String cartNo);
}
