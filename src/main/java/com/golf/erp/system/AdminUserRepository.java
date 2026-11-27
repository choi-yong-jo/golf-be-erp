package com.golf.erp.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long>, JpaSpecificationExecutor<AdminUser> {
	Optional<AdminUser> findByUsername(String username);

	boolean existsByUsername(String username);

	@Query("select count(u) from AdminUser u where u.role = 'ADMIN' and u.active = true")
	long countActiveAdmins();
}
