package com.golf.erp.board;

/**
 * board.inquiry_status (INQUIRY 만, 필수). 허용 전이:
 * <pre>
 * RECEIVED    → IN_PROGRESS, ANSWERED, CLOSED
 * IN_PROGRESS → ANSWERED, CLOSED
 * ANSWERED    → IN_PROGRESS(추가 처리), CLOSED
 * CLOSED      → IN_PROGRESS(재개), ANSWERED
 * </pre>
 * ANSWERED 로 가려면 답변이 있어야 한다. RECEIVED 로는 되돌릴 수 없다.
 */
public enum InquiryStatus {
	RECEIVED, IN_PROGRESS, ANSWERED, CLOSED;

	public boolean canMoveTo(InquiryStatus next) {
		return switch (this) {
		case RECEIVED -> next == IN_PROGRESS || next == ANSWERED || next == CLOSED;
		case IN_PROGRESS -> next == ANSWERED || next == CLOSED;
		case ANSWERED -> next == IN_PROGRESS || next == CLOSED;
		case CLOSED -> next == IN_PROGRESS || next == ANSWERED;
		};
	}
}
