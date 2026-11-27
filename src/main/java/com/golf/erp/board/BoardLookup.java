package com.golf.erp.board;

import com.golf.erp.board.BoardDtos.Attachment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 게시판이 읽는 다른 모듈 테이블(admin_user, member, reservation, common_file)과 첨부 집계.
 * 다른 모듈 엔티티를 참조하지 않으려고 필요한 컬럼만 SQL 로 읽는다.
 */
@Component
class BoardLookup {
	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate named;

	BoardLookup(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.named = new NamedParameterJdbcTemplate(jdbc);
	}

	/** admin_user_id → display_name */
	Map<Long, String> adminNames(Collection<Long> ids) {
		return names("SELECT admin_user_id, display_name FROM golf.admin_user WHERE admin_user_id IN (:ids)", ids);
	}

	/** member_id → member_name */
	Map<Long, String> memberNames(Collection<Long> ids) {
		return names("SELECT member_id, member_name FROM golf.member WHERE member_id IN (:ids)", ids);
	}

	/** board_id → 첨부 수 */
	Map<Long, Integer> attachmentCounts(Collection<Long> boardIds) {
		Map<Long, Integer> m = new HashMap<>();
		List<Long> ids = nonNull(boardIds);
		if (ids.isEmpty())
			return m;
		named.query("SELECT board_id, COUNT(*) FROM golf.board_file WHERE board_id IN (:ids) GROUP BY board_id",
				new MapSqlParameterSource("ids", ids), rs -> {
					m.put(rs.getLong(1), rs.getInt(2));
				});
		return m;
	}

	/** 게시글 첨부와 파일 메타데이터(첨부 순서대로) */
	List<Attachment> attachments(long boardId) {
		return jdbc.query("SELECT bf.common_file_id, bf.display_order, bf.caption, bf.attached_at,"
				+ " f.original_filename, f.file_extension, f.mime_type, f.file_category, f.file_size_bytes,"
				+ " f.image_width, f.image_height, f.upload_status"
				+ " FROM golf.board_file bf JOIN golf.common_file f ON f.common_file_id = bf.common_file_id"
				+ " WHERE bf.board_id = ? ORDER BY bf.display_order, bf.attached_at, bf.common_file_id", (rs, i) -> {
					long fileId = rs.getLong("common_file_id");
					Timestamp attachedAt = rs.getTimestamp("attached_at");
					return new Attachment(fileId, rs.getShort("display_order"), rs.getString("caption"),
							attachedAt == null ? null : attachedAt.toLocalDateTime(), rs.getString("original_filename"),
							rs.getString("file_extension"), rs.getString("mime_type"), rs.getString("file_category"),
							rs.getLong("file_size_bytes"), rs.getObject("image_width", Integer.class),
							rs.getObject("image_height", Integer.class), rs.getString("upload_status"),
							"/api/files/" + fileId + "/download");
				}, boardId);
	}

	/**
	 * common_file_id → upload_status. 첨부하는 동안 파일이 삭제되지 않도록 FOR SHARE 로 잠근다
	 * (파일 삭제는 같은 행을 FOR UPDATE 로 잠근 뒤 첨부 여부를 확인한다). 없는 id 는 결과에 없다.
	 */
	Map<Long, String> lockFileStatuses(Collection<Long> fileIds) {
		Map<Long, String> m = new HashMap<>();
		List<Long> ids = nonNull(fileIds);
		if (ids.isEmpty())
			return m;
		named.query("SELECT common_file_id, upload_status FROM golf.common_file WHERE common_file_id IN (:ids)"
				+ " ORDER BY common_file_id FOR SHARE", new MapSqlParameterSource("ids", ids), rs -> {
					m.put(rs.getLong(1), rs.getString(2));
				});
		return m;
	}

	/** 예약의 회원 id (예약이 없으면 null) */
	Long reservationMemberId(long reservationId) {
		List<Long> ids = jdbc.queryForList("SELECT member_id FROM golf.reservation WHERE reservation_id = ?",
				Long.class, reservationId);
		return ids.isEmpty() ? null : ids.get(0);
	}

	private Map<Long, String> names(String sql, Collection<Long> ids) {
		Map<Long, String> m = new HashMap<>();
		List<Long> list = nonNull(ids);
		if (list.isEmpty())
			return m;
		named.query(sql, new MapSqlParameterSource("ids", list), rs -> {
			m.put(rs.getLong(1), rs.getString(2));
		});
		return m;
	}

	private static List<Long> nonNull(Collection<Long> ids) {
		return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
	}
}
