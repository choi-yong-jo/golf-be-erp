package com.golf.erp.board;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/** golf.board_file — 게시글 첨부(게시글 ↔ 공통 파일). 파일 메타데이터는 common_file 에 있다. */
@Entity
@Table(name = "board_file")
@IdClass(BoardFile.Key.class)
public class BoardFile {
	@Id
	@Column(name = "board_id")
	private Long boardId;
	@Id
	@Column(name = "common_file_id")
	private Long commonFileId;
	@Column(name = "display_order", nullable = false)
	private Short displayOrder;
	@Column(name = "caption", length = 300)
	private String caption;
	@Column(name = "attached_at", nullable = false, updatable = false)
	private LocalDateTime attachedAt;

	protected BoardFile() {
	}

	public BoardFile(Long boardId, Long commonFileId, short displayOrder, String caption, LocalDateTime attachedAt) {
		this.boardId = boardId;
		this.commonFileId = commonFileId;
		this.attachedAt = attachedAt;
		update(displayOrder, caption);
	}

	public void update(short displayOrder, String caption) {
		if (displayOrder < 0)
			throw new IllegalArgumentException("첨부 순서는 0 이상이어야 합니다.");
		this.displayOrder = displayOrder;
		this.caption = caption;
	}

	public Long getBoardId() {
		return boardId;
	}

	public Long getCommonFileId() {
		return commonFileId;
	}

	public Short getDisplayOrder() {
		return displayOrder;
	}

	public String getCaption() {
		return caption;
	}

	public LocalDateTime getAttachedAt() {
		return attachedAt;
	}

	public static class Key implements Serializable {
		private Long boardId;
		private Long commonFileId;

		public Key() {
		}

		public Key(Long boardId, Long commonFileId) {
			this.boardId = boardId;
			this.commonFileId = commonFileId;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && Objects.equals(boardId, k.boardId)
					&& Objects.equals(commonFileId, k.commonFileId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(boardId, commonFileId);
		}
	}
}
