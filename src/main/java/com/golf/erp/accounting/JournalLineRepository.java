package com.golf.erp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {
	boolean existsByAccountId(Long accountId);

	/** 전표별 [journal_entry_id, 차변 합계, 대변 합계] */
	@Query("select l.entry.id, sum(l.debitAmount), sum(l.creditAmount) from JournalLine l"
			+ " where l.entry.id in :entryIds group by l.entry.id")
	List<Object[]> sumByEntryIds(@Param("entryIds") Collection<Long> entryIds);
}
