package com.golf.erp.board;

/** board.board_type */
public enum BoardType {
	NOTICE, EVENT, FAQ, INQUIRY;

	/** 답변(answer_*)을 가질 수 있는 유형. 나머지는 answer_status = NOT_REQUIRED (ck_board_answer_state) */
	public boolean answerable() {
		return this == FAQ || this == INQUIRY;
	}

	/** 분류(inquiry_category)를 쓰는 유형 */
	public boolean categorized() {
		return this == FAQ || this == INQUIRY;
	}
}
