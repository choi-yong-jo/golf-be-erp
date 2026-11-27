package com.golf.erp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {
	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

	boolean existsByParentAccountId(Long parentAccountId);

	boolean existsByParentAccountIdAndActiveTrue(Long parentAccountId);
}
