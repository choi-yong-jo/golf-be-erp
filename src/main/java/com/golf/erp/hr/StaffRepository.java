package com.golf.erp.hr;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long>, JpaSpecificationExecutor<Staff> {
	boolean existsByStaffNo(String staffNo);

	boolean existsByStaffNoAndIdNot(String staffNo, Long id);

	boolean existsByAdminUserId(Long adminUserId);

	boolean existsByAdminUserIdAndIdNot(Long adminUserId, Long id);

	List<Staff> findByStatusOrderByStaffNo(StaffStatus status);

	/** 같은 직원의 근무 일정 등록·수정을 한 줄로 세우려고 직원 행을 잠근다(겹침 검사용). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Staff s where s.id = :id")
	Optional<Staff> findByIdForUpdate(@Param("id") Long id);
}
