package com.golf.erp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StaffShiftRepository extends JpaRepository<StaffShift, Long>, JpaSpecificationExecutor<StaffShift> {
	List<StaffShift> findByStaffIdAndWorkDateBetweenAndStatusIn(Long staffId, LocalDate from, LocalDate to,
			Collection<ShiftStatus> statuses);

	long countByStaffIdAndStatusAndWorkDateGreaterThanEqual(Long staffId, ShiftStatus status, LocalDate from);

	boolean existsByStaffId(Long staffId);
}
