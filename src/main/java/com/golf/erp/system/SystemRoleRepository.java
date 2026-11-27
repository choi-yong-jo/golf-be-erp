package com.golf.erp.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemRoleRepository extends JpaRepository<SystemRole, Long> {
	boolean existsByCode(String code);

	List<SystemRole> findAllByOrderByCodeAsc();
}
