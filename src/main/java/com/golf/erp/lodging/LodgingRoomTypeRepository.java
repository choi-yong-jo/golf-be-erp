package com.golf.erp.lodging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LodgingRoomTypeRepository
		extends JpaRepository<LodgingRoomType, Long>, JpaSpecificationExecutor<LodgingRoomType> {

	boolean existsByGolfCourseIdAndCode(Long golfCourseId, String code);
}
