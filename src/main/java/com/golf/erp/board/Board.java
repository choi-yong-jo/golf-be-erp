package com.golf.erp.board;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * golf.board — 공지(NOTICE)·이벤트(EVENT)·FAQ·고객 문의(INQUIRY) 통합 게시판. FAQ 답변과 문의 답변은 answer_* 컬럼에 둔다.
 * <p>
 * DDL CHECK 가 강제하는 규칙을 이 클래스의 메서드가 지킨다.
 * <ul>
 * <li>INQUIRY: inquiry_status 필수, visibility = PRIVATE</li>
 * <li>NOTICE/EVENT: answer_status = NOT_REQUIRED (답변 불가)</li>
 * <li>answer_content 와 answered_at 은 함께 있거나 함께 없다. ANSWERED 면 answer_content 필수</li>
 * <li>게시·행사 기간: 끝 ≥ 시작, display_order ≥ 0</li>
 * </ul>
 * 삭제는 deleted_at 을 채우고 post_status 를 CLOSED 로 바꾼다(deleted_at 을 안 보는 조회도 게시 중으로 보지 않게).
 * 삭제된 글은 더 이상 바꿀 수 없다.
 */
@Entity
@Table(name = "board")
public class Board {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "board_id")
	private Long id;
	@Enumerated(EnumType.STRING)
	@Column(name = "board_type", nullable = false, length = 20, updatable = false)
	private BoardType type;
	@Column(name = "title", nullable = false, length = 300)
	private String title;
	@Column(name = "content", nullable = false, columnDefinition = "text")
	private String content;
	@Column(name = "author_member_id")
	private Long authorMemberId;
	@Column(name = "author_admin_user_id")
	private Long authorAdminUserId;
	@Column(name = "guest_name", length = 100)
	private String guestName;
	@Column(name = "guest_phone", length = 30)
	private String guestPhone;
	@Column(name = "guest_email", length = 255)
	private String guestEmail;
	@Column(name = "reservation_id")
	private Long reservationId;
	@Column(name = "inquiry_category", length = 40)
	private String inquiryCategory;
	@Enumerated(EnumType.STRING)
	@Column(name = "inquiry_status", length = 20)
	private InquiryStatus inquiryStatus;
	@Column(name = "answer_content", columnDefinition = "text")
	private String answerContent;
	@Column(name = "answered_by")
	private Long answeredBy;
	@Column(name = "answered_at")
	private LocalDateTime answeredAt;
	@Enumerated(EnumType.STRING)
	@Column(name = "answer_status", nullable = false, length = 20)
	private AnswerStatus answerStatus = AnswerStatus.NOT_REQUIRED;
	@Enumerated(EnumType.STRING)
	@Column(name = "visibility", nullable = false, length = 20)
	private BoardVisibility visibility = BoardVisibility.PUBLIC;
	@Enumerated(EnumType.STRING)
	@Column(name = "post_status", nullable = false, length = 20)
	private PostStatus postStatus = PostStatus.PUBLISHED;
	@Column(name = "is_pinned", nullable = false)
	private boolean pinned;
	@Column(name = "display_order", nullable = false)
	private int displayOrder;
	@Column(name = "publish_start_at")
	private LocalDateTime publishStartAt;
	@Column(name = "publish_end_at")
	private LocalDateTime publishEndAt;
	@Column(name = "event_start_at")
	private LocalDateTime eventStartAt;
	@Column(name = "event_end_at")
	private LocalDateTime eventEndAt;
	@Column(name = "view_count", nullable = false)
	private Long viewCount = 0L;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected Board() {
	}

	/**
	 * 새 글. FAQ/INQUIRY 는 답변 대기(WAITING), 그 밖은 NOT_REQUIRED. INQUIRY 는 접수(RECEIVED)·비공개(PRIVATE)로 시작한다.
	 *
	 * @param authorAdminUserId 작성(INQUIRY 는 대리 등록)한 직원
	 */
	public Board(BoardType type, String title, String content, Long authorAdminUserId, PostStatus postStatus,
			LocalDateTime now) {
		if (type == BoardType.INQUIRY && postStatus == PostStatus.DRAFT)
			throw new IllegalArgumentException("고객 문의는 임시저장(DRAFT)으로 등록할 수 없습니다.");
		this.type = type;
		this.title = title;
		this.content = content;
		this.authorAdminUserId = authorAdminUserId;
		this.postStatus = postStatus;
		this.answerStatus = type.answerable() ? AnswerStatus.WAITING : AnswerStatus.NOT_REQUIRED;
		if (type == BoardType.INQUIRY) {
			this.inquiryStatus = InquiryStatus.RECEIVED;
			this.visibility = BoardVisibility.PRIVATE;
		}
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** 문의 작성자(회원 또는 비회원)와 연결 예약. 등록할 때 한 번만 넣는다. */
	public void registerInquiry(Long memberId, String guestName, String guestPhone, String guestEmail,
			Long reservationId, String category) {
		if (type != BoardType.INQUIRY || id != null)
			throw new IllegalStateException("문의 작성자 정보는 문의 등록 시에만 넣을 수 있습니다.");
		if (memberId == null && guestName == null)
			throw new IllegalArgumentException("문의에는 회원(memberId) 또는 비회원 이름(guestName)이 필요합니다.");
		this.authorMemberId = memberId;
		this.guestName = guestName;
		this.guestPhone = guestPhone;
		this.guestEmail = guestEmail;
		this.reservationId = reservationId;
		this.inquiryCategory = category;
	}

	/** 공지·이벤트·FAQ 의 내용과 게시 설정 수정. 고객 문의는 {@link #changeCategory} 만 쓸 수 있다. */
	public void edit(String title, String content, BoardVisibility visibility, boolean pinned, int displayOrder,
			LocalDateTime publishStartAt, LocalDateTime publishEndAt, LocalDateTime eventStartAt,
			LocalDateTime eventEndAt, String category, LocalDateTime now) {
		requireNotDeleted();
		if (type == BoardType.INQUIRY)
			throw new IllegalArgumentException("고객 문의는 분류(inquiryCategory)만 수정할 수 있습니다.");
		if (displayOrder < 0)
			throw new IllegalArgumentException("정렬 순서는 0 이상이어야 합니다.");
		requirePeriod(publishStartAt, publishEndAt, "게시");
		requirePeriod(eventStartAt, eventEndAt, "행사");
		if (type != BoardType.EVENT && (eventStartAt != null || eventEndAt != null))
			throw new IllegalArgumentException("행사 기간은 이벤트(EVENT) 글에만 넣을 수 있습니다.");
		requireCategoryAllowed(category);
		this.title = title;
		this.content = content;
		this.visibility = visibility;
		this.pinned = pinned;
		this.displayOrder = displayOrder;
		this.publishStartAt = publishStartAt;
		this.publishEndAt = publishEndAt;
		this.eventStartAt = eventStartAt;
		this.eventEndAt = eventEndAt;
		this.inquiryCategory = category;
		this.updatedAt = now;
	}

	/** 분류만 변경(FAQ·INQUIRY) */
	public void changeCategory(String category, LocalDateTime now) {
		requireNotDeleted();
		requireCategoryAllowed(category);
		this.inquiryCategory = category;
		this.updatedAt = now;
	}

	/**
	 * 답변 등록·수정(FAQ·INQUIRY). 문의는 ANSWERED 가 된다. 종료(CLOSED)된 문의는 먼저 상태를 되돌려야 한다.
	 *
	 * @return 새 답변이면 true, 기존 답변 수정이면 false
	 */
	public boolean answer(String answerContent, Long answeredBy, LocalDateTime now) {
		requireNotDeleted();
		if (!type.answerable())
			throw new IllegalArgumentException("FAQ·문의 글에만 답변할 수 있습니다.");
		if (inquiryStatus == InquiryStatus.CLOSED)
			throw new ConflictException("종료된 문의입니다. 문의 상태를 처리 중으로 바꾼 뒤 답변하세요.");
		boolean isNew = this.answerContent == null;
		this.answerContent = answerContent;
		this.answeredBy = answeredBy;
		this.answeredAt = now;
		this.answerStatus = AnswerStatus.ANSWERED;
		if (type == BoardType.INQUIRY)
			this.inquiryStatus = InquiryStatus.ANSWERED;
		this.updatedAt = now;
		return isNew;
	}

	/** 답변 삭제 → 답변 대기(WAITING). 문의는 처리 중(IN_PROGRESS)으로 돌아간다. */
	public void clearAnswer(LocalDateTime now) {
		requireNotDeleted();
		if (!type.answerable())
			throw new IllegalArgumentException("FAQ·문의 글에만 답변이 있습니다.");
		if (answerContent == null)
			throw new ConflictException("등록된 답변이 없습니다.");
		if (inquiryStatus == InquiryStatus.CLOSED)
			throw new ConflictException("종료된 문의의 답변은 삭제할 수 없습니다. 문의 상태를 먼저 바꾸세요.");
		this.answerContent = null;
		this.answeredBy = null;
		this.answeredAt = null;
		this.answerStatus = AnswerStatus.WAITING;
		if (type == BoardType.INQUIRY)
			this.inquiryStatus = InquiryStatus.IN_PROGRESS;
		this.updatedAt = now;
	}

	/** 게시 상태 변경. 고객 문의는 DRAFT 로 바꿀 수 없다. @return 바꾸기 전 상태 */
	public PostStatus changePostStatus(PostStatus next, LocalDateTime now) {
		requireNotDeleted();
		if (type == BoardType.INQUIRY && next == PostStatus.DRAFT)
			throw new ConflictException("고객 문의는 임시저장(DRAFT) 상태로 바꿀 수 없습니다.");
		PostStatus before = this.postStatus;
		this.postStatus = next;
		this.updatedAt = now;
		return before;
	}

	/**
	 * 문의 처리 상태 변경. 전이 규칙은 {@link InquiryStatus#canMoveTo}, ANSWERED 는 답변이 있어야 한다.
	 *
	 * @return 바뀌었으면 true (같은 상태면 false)
	 */
	public boolean changeInquiryStatus(InquiryStatus next, LocalDateTime now) {
		requireNotDeleted();
		if (type != BoardType.INQUIRY)
			throw new IllegalArgumentException("고객 문의(INQUIRY)에만 처리 상태가 있습니다.");
		if (inquiryStatus == next)
			return false;
		if (!inquiryStatus.canMoveTo(next))
			throw new ConflictException("문의 상태를 " + inquiryStatus + "에서 " + next + "(으)로 바꿀 수 없습니다.");
		if (next == InquiryStatus.ANSWERED && answerContent == null)
			throw new ConflictException("답변을 먼저 등록하세요.");
		this.inquiryStatus = next;
		this.updatedAt = now;
		return true;
	}

	/** 삭제 표시(deleted_at) + post_status CLOSED. 첨부(board_file)와 답변은 이력으로 남긴다. */
	public void delete(LocalDateTime now) {
		requireNotDeleted();
		this.deletedAt = now;
		this.postStatus = PostStatus.CLOSED;
		this.updatedAt = now;
	}

	/** 첨부 변경도 글 수정으로 본다. */
	public void touch(LocalDateTime now) {
		requireNotDeleted();
		this.updatedAt = now;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	private void requireNotDeleted() {
		if (deletedAt != null)
			throw new ConflictException("삭제된 게시글입니다.");
	}

	private void requireCategoryAllowed(String category) {
		if (category != null && !type.categorized())
			throw new IllegalArgumentException("분류(inquiryCategory)는 FAQ·문의 글에만 넣을 수 있습니다.");
	}

	private static void requirePeriod(LocalDateTime start, LocalDateTime end, String what) {
		if (start != null && end != null && end.isBefore(start))
			throw new IllegalArgumentException(what + " 종료 일시는 시작 일시보다 빠를 수 없습니다.");
	}

	public Long getId() {
		return id;
	}

	public BoardType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public Long getAuthorMemberId() {
		return authorMemberId;
	}

	public Long getAuthorAdminUserId() {
		return authorAdminUserId;
	}

	public String getGuestName() {
		return guestName;
	}

	public String getGuestPhone() {
		return guestPhone;
	}

	public String getGuestEmail() {
		return guestEmail;
	}

	public Long getReservationId() {
		return reservationId;
	}

	public String getInquiryCategory() {
		return inquiryCategory;
	}

	public InquiryStatus getInquiryStatus() {
		return inquiryStatus;
	}

	public String getAnswerContent() {
		return answerContent;
	}

	public Long getAnsweredBy() {
		return answeredBy;
	}

	public LocalDateTime getAnsweredAt() {
		return answeredAt;
	}

	public AnswerStatus getAnswerStatus() {
		return answerStatus;
	}

	public BoardVisibility getVisibility() {
		return visibility;
	}

	public PostStatus getPostStatus() {
		return postStatus;
	}

	public boolean isPinned() {
		return pinned;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public LocalDateTime getPublishStartAt() {
		return publishStartAt;
	}

	public LocalDateTime getPublishEndAt() {
		return publishEndAt;
	}

	public LocalDateTime getEventStartAt() {
		return eventStartAt;
	}

	public LocalDateTime getEventEndAt() {
		return eventEndAt;
	}

	public Long getViewCount() {
		return viewCount;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}
}
