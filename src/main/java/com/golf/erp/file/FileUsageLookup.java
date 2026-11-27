package com.golf.erp.file;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 파일이 게시글에 첨부돼 있는지. 게시판 모듈 엔티티를 참조하지 않으려고 SQL 로 읽는다. */
@Component
class FileUsageLookup {
	private final JdbcTemplate jdbc;

	FileUsageLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 이 파일을 첨부한 삭제되지 않은 게시글 id 하나(없으면 null) */
	Long activeBoardUsing(long fileId) {
		List<Long> ids = jdbc.queryForList("SELECT bf.board_id FROM golf.board_file bf"
				+ " JOIN golf.board b ON b.board_id = bf.board_id"
				+ " WHERE bf.common_file_id = ? AND b.deleted_at IS NULL ORDER BY bf.board_id LIMIT 1", Long.class,
				fileId);
		return ids.isEmpty() ? null : ids.get(0);
	}

	/** 이 파일을 이미지로 쓰는 매장 메뉴 이름 하나(없으면 null) */
	String menuItemUsing(long fileId) {
		List<String> names = jdbc.queryForList("SELECT menu_name FROM golf.store_menu_item"
				+ " WHERE menu_image_file_id = ? ORDER BY store_menu_item_id LIMIT 1", String.class, fileId);
		return names.isEmpty() ? null : names.get(0);
	}
}
