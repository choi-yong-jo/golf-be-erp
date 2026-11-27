package com.golf.erp.board;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 게시판 API 요청/응답 */
public final class BoardDtos {
	/** 게시글 하나에 붙일 수 있는 첨부 수 */
	public static final int MAX_ATTACHMENTS = 30;

	private BoardDtos() {
	}

	/** 첨부 하나. displayOrder 를 생략하면 목록 순서(0부터) */
	public record AttachmentRequest(@NotNull Long fileId, @PositiveOrZero @Max(Short.MAX_VALUE) Integer displayOrder,
			@Size(max = 300) String caption) {
	}

	/**
	 * 게시글 등록.
	 * <ul>
	 * <li>NOTICE/EVENT/FAQ: 작성자 = 로그인 직원. eventStartAt/EndAt 은 EVENT 만, inquiryCategory 는 FAQ 만,
	 * answerContent(등록과 동시에 답변)는 FAQ 만</li>
	 * <li>INQUIRY(전화 문의 대리 등록): memberId 또는 guestName(+ guestPhone/guestEmail 중 하나) 필요. visibility 는 항상
	 * PRIVATE, pinned·displayOrder·게시/행사 기간·answerContent 는 넣을 수 없다</li>
	 * </ul>
	 * postStatus: DRAFT 또는 PUBLISHED(기본)
	 */
	public record BoardCreateRequest(@NotNull BoardType type, @NotBlank @Size(max = 300) String title,
			@NotBlank String content, BoardVisibility visibility, PostStatus postStatus, Boolean pinned,
			@PositiveOrZero Integer displayOrder, LocalDateTime publishStartAt, LocalDateTime publishEndAt,
			LocalDateTime eventStartAt, LocalDateTime eventEndAt, @Size(max = 40) String inquiryCategory,
			Long memberId, @Size(max = 100) String guestName, @Size(max = 30) String guestPhone,
			@Email @Size(max = 255) String guestEmail, Long reservationId, String answerContent,
			@Valid @Size(max = MAX_ATTACHMENTS) List<AttachmentRequest> files) {
	}

	/**
	 * 게시글 수정(PUT). title·content 는 필수(INQUIRY 제외). visibility·pinned·displayOrder 는 null 이면 그대로 두고,
	 * 기간·분류는 null 이면 지운다. INQUIRY 는 inquiryCategory 만 바꿀 수 있다(나머지 값은 비우거나 지금 값 그대로 보낸다).
	 */
	public record BoardUpdateRequest(@Size(max = 300) String title, String content, BoardVisibility visibility,
			Boolean pinned, @PositiveOrZero Integer displayOrder, LocalDateTime publishStartAt,
			LocalDateTime publishEndAt, LocalDateTime eventStartAt, LocalDateTime eventEndAt,
			@Size(max = 40) String inquiryCategory) {
	}

	public record PostStatusRequest(@NotNull PostStatus status) {
	}

	public record InquiryStatusRequest(@NotNull InquiryStatus status) {
	}

	public record AnswerRequest(@NotBlank String answerContent) {
	}

	/** 첨부 전체 교체. 빈 목록이면 첨부를 모두 해제한다. */
	public record AttachmentsRequest(@NotNull @Valid @Size(max = MAX_ATTACHMENTS) List<AttachmentRequest> files) {
	}

	/**
	 * 작성자 표시. authorType: MEMBER(회원 문의), GUEST(비회원 문의), ADMIN(직원 작성).
	 * 직원이 대리 등록한 문의는 MEMBER/GUEST 이고, 등록한 직원은 상세의 authorAdminUserId 로 본다.
	 */
	public record Author(String type, String name) {
		static Author of(Board b, String memberName, String adminName) {
			if (b.getAuthorMemberId() != null)
				return new Author("MEMBER", memberName);
			if (b.getGuestName() != null)
				return new Author("GUEST", b.getGuestName());
			return new Author("ADMIN", adminName);
		}
	}

	/** 목록 행 */
	public record BoardRow(Long id, BoardType type, String title, PostStatus postStatus, BoardVisibility visibility,
			String inquiryCategory, InquiryStatus inquiryStatus, AnswerStatus answerStatus, boolean pinned,
			int displayOrder, Long viewCount, String authorType, String authorName, int attachmentCount,
			LocalDateTime publishStartAt, LocalDateTime publishEndAt, LocalDateTime eventStartAt,
			LocalDateTime eventEndAt, LocalDateTime answeredAt, LocalDateTime createdAt, LocalDateTime updatedAt,
			LocalDateTime deletedAt) {

		static BoardRow of(Board b, Author author, int attachmentCount) {
			return new BoardRow(b.getId(), b.getType(), b.getTitle(), b.getPostStatus(), b.getVisibility(),
					b.getInquiryCategory(), b.getInquiryStatus(), b.getAnswerStatus(), b.isPinned(),
					b.getDisplayOrder(), b.getViewCount(), author.type(), author.name(), attachmentCount,
					b.getPublishStartAt(), b.getPublishEndAt(), b.getEventStartAt(), b.getEventEndAt(),
					b.getAnsweredAt(), b.getCreatedAt(), b.getUpdatedAt(), b.getDeletedAt());
		}
	}

	/** 첨부 파일(common_file 메타데이터 포함). fileCategory·uploadStatus 는 common_file 값 그대로 */
	public record Attachment(Long fileId, Short displayOrder, String caption, LocalDateTime attachedAt,
			String originalFilename, String fileExtension, String mimeType, String fileCategory, Long fileSizeBytes,
			Integer imageWidth, Integer imageHeight, String uploadStatus, String downloadUrl) {
	}

	/** 상세. ERP 조회는 조회수(viewCount)를 올리지 않는다. */
	public record BoardDetail(Long id, BoardType type, String title, String content, PostStatus postStatus,
			BoardVisibility visibility, boolean pinned, int displayOrder, Long viewCount, LocalDateTime publishStartAt,
			LocalDateTime publishEndAt, LocalDateTime eventStartAt, LocalDateTime eventEndAt, String authorType,
			String authorName, Long authorMemberId, Long authorAdminUserId, String authorAdminName, String guestName,
			String guestPhone, String guestEmail, Long reservationId, String inquiryCategory,
			InquiryStatus inquiryStatus, AnswerStatus answerStatus, String answerContent, Long answeredBy,
			String answeredByName, LocalDateTime answeredAt, List<Attachment> attachments, LocalDateTime createdAt,
			LocalDateTime updatedAt, LocalDateTime deletedAt) {

		static BoardDetail of(Board b, Author author, String authorAdminName, String answeredByName,
				List<Attachment> attachments) {
			return new BoardDetail(b.getId(), b.getType(), b.getTitle(), b.getContent(), b.getPostStatus(),
					b.getVisibility(), b.isPinned(), b.getDisplayOrder(), b.getViewCount(), b.getPublishStartAt(),
					b.getPublishEndAt(), b.getEventStartAt(), b.getEventEndAt(), author.type(), author.name(),
					b.getAuthorMemberId(), b.getAuthorAdminUserId(), authorAdminName, b.getGuestName(),
					b.getGuestPhone(), b.getGuestEmail(), b.getReservationId(), b.getInquiryCategory(),
					b.getInquiryStatus(), b.getAnswerStatus(), b.getAnswerContent(), b.getAnsweredBy(), answeredByName,
					b.getAnsweredAt(), attachments, b.getCreatedAt(), b.getUpdatedAt(), b.getDeletedAt());
		}
	}

	/** 삭제되지 않은 고객 문의 건수(처리 상태별) */
	public record InquirySummary(long received, long inProgress, long answered, long closed, long total) {
	}
}
