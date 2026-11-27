package com.golf.erp.crm;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerNotificationRepository
		extends JpaRepository<CustomerNotification, Long>, JpaSpecificationExecutor<CustomerNotification> {

	/** 발송 결과 기록·취소가 동시에 들어와도 한 번만 반영되도록 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from CustomerNotification n where n.id = :id")
	Optional<CustomerNotification> findByIdForUpdate(@Param("id") long id);
}
