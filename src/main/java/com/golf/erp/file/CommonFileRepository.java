package com.golf.erp.file;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommonFileRepository extends JpaRepository<CommonFile, Long>, JpaSpecificationExecutor<CommonFile> {

	/** 삭제 시 게시글 첨부(board_file, 행을 FOR SHARE 로 읽음)와 동시에 진행되지 않도록 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from CommonFile f where f.id = :id")
	Optional<CommonFile> findByIdForUpdate(@Param("id") Long id);
}
