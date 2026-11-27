package com.golf.erp.accounting;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long>, JpaSpecificationExecutor<JournalEntry> {
	/** 상태를 바꾸는 처리(수정·전기·무효·삭제)는 전표 행을 잠그고 진행한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from JournalEntry e where e.id = :id")
	Optional<JournalEntry> findByIdForUpdate(@Param("id") Long id);
}
