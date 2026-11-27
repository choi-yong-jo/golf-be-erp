package com.golf.erp.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ServiceFeeRepository extends JpaRepository<ServiceFee, Long>, JpaSpecificationExecutor<ServiceFee> {
	/** 기간 겹침 검사 후보(같은 골프장·종류·이름의 사용 중인 요금) */
	List<ServiceFee> findByGolfCourseIdAndFeeTypeAndNameAndActiveTrue(Long golfCourseId, FeeType feeType, String name);
}
