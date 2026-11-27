package com.golf.erp.board;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long>, JpaSpecificationExecutor<Board> {

	/** 상태·답변·첨부 변경은 동시에 하나만(예약 앱의 고객 수정과도 겹치지 않게) */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Board b where b.id = :id")
	Optional<Board> findByIdForUpdate(@Param("id") Long id);

	/** [inquiryStatus, count] — 삭제되지 않은 문의 */
	@Query("select b.inquiryStatus, count(b) from Board b where b.type = :type and b.deletedAt is null"
			+ " group by b.inquiryStatus")
	List<Object[]> countByInquiryStatus(@Param("type") BoardType type);
}
