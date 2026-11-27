package com.golf.erp.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CaddieRepository extends JpaRepository<Caddie, Long>, JpaSpecificationExecutor<Caddie> {
	boolean existsByGolfCourseIdAndCaddieNo(Long golfCourseId, String caddieNo);
}
