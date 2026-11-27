package com.golf.erp.hr;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long>, JpaSpecificationExecutor<Payroll> {
	boolean existsByStaffIdAndPayPeriod(Long staffId, LocalDate payPeriod);

	boolean existsByStaffId(Long staffId);

	/** 해당 월에 이미 급여 행이 있는 직원 id */
	@Query("select p.staffId from Payroll p where p.payPeriod = :period")
	List<Long> findStaffIdsByPayPeriod(@Param("period") LocalDate period);

	/** 직원별로 period 이전 가장 최근 급여 행(기본급 이월용) */
	@Query("select p from Payroll p where p.payPeriod = (select max(p2.payPeriod) from Payroll p2"
			+ " where p2.staffId = p.staffId and p2.payPeriod < :period)")
	List<Payroll> findLatestBefore(@Param("period") LocalDate period);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payroll p where p.id = :id")
	Optional<Payroll> findByIdForUpdate(@Param("id") Long id);
}
