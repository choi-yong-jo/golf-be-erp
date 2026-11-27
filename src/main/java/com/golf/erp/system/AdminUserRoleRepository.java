package com.golf.erp.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface AdminUserRoleRepository extends JpaRepository<AdminUserRole, AdminUserRole.Key> {
	List<AdminUserRole> findByAdminUserId(Long adminUserId);

	List<AdminUserRole> findByAdminUserIdIn(Collection<Long> adminUserIds);

	@Modifying(flushAutomatically = true)
	@Query("delete from AdminUserRole r where r.adminUserId = :adminUserId")
	void deleteByAdminUserId(Long adminUserId);
}
