package com.golf.erp.board;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardFileRepository extends JpaRepository<BoardFile, BoardFile.Key> {
	List<BoardFile> findByBoardId(Long boardId);
}
