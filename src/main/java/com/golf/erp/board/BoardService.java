package com.golf.erp.board;

import com.golf.erp.board.BoardDtos.AttachmentRequest;
import com.golf.erp.board.BoardDtos.Author;
import com.golf.erp.board.BoardDtos.BoardCreateRequest;
import com.golf.erp.board.BoardDtos.BoardDetail;
import com.golf.erp.board.BoardDtos.BoardRow;
import com.golf.erp.board.BoardDtos.BoardUpdateRequest;
import com.golf.erp.board.BoardDtos.InquirySummary;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ERP(직원) 쪽 게시판 관리. 고객은 예약 앱에서 문의를 올리고, 직원은 여기서 모든 글을 관리한다.
 * ERP 조회는 조회수를 올리지 않는다.
 */
@Service
@Transactional
public class BoardService {
	private static final String TABLE = "board";

	private final BoardRepository repo;
	private final BoardFileRepository files;
	private final BoardLookup lookup;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public BoardService(BoardRepository repo, BoardFileRepository files, BoardLookup lookup, RefChecker refs,
			AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.files = files;
		this.lookup = lookup;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * 목록. 정렬: 상단 고정 → display_order → 최신 등록 순.
	 *
	 * @param keyword        제목·내용
	 * @param includeDeleted true 면 삭제된 글도 포함
	 */
	@Transactional(readOnly = true)
	public PageResult<BoardRow> list(BoardType type, PostStatus postStatus, BoardVisibility visibility,
			InquiryStatus inquiryStatus, AnswerStatus answerStatus, Boolean pinned, String keyword, LocalDate from,
			LocalDate to, boolean includeDeleted, int page, int size) {
		Specification<Board> spec = Specification.allOf(Specs.eq("type", type), Specs.eq("postStatus", postStatus),
				Specs.eq("visibility", visibility), Specs.eq("inquiryStatus", inquiryStatus),
				Specs.eq("answerStatus", answerStatus), Specs.eq("pinned", pinned),
				Specs.like(keyword, "title", "content"),
				Specs.gte("createdAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("createdAt", to == null ? null : to.plusDays(1).atStartOfDay()),
				includeDeleted ? null : Specs.isNull("deletedAt"));
		Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.asc("displayOrder"), Sort.Order.desc("createdAt"),
				Sort.Order.desc("id"));
		Page<Board> result = repo.findAll(spec, Pages.of(page, size, sort));

		List<Board> boards = result.getContent();
		Map<Long, String> admins = lookup.adminNames(boards.stream().map(Board::getAuthorAdminUserId).toList());
		Map<Long, String> members = lookup.memberNames(boards.stream().map(Board::getAuthorMemberId).toList());
		Map<Long, Integer> counts = lookup.attachmentCounts(boards.stream().map(Board::getId).toList());
		return PageResult.of(result,
				b -> BoardRow.of(b,
						Author.of(b, members.get(b.getAuthorMemberId()), admins.get(b.getAuthorAdminUserId())),
						counts.getOrDefault(b.getId(), 0)));
	}

	/** 상세(삭제된 글도 조회 가능). 조회수는 올리지 않는다. */
	@Transactional(readOnly = true)
	public BoardDetail get(long id) {
		return detail(find(id));
	}

	public BoardDetail create(BoardCreateRequest r) {
		PostStatus postStatus = r.postStatus() == null ? PostStatus.PUBLISHED : r.postStatus();
		if (postStatus != PostStatus.DRAFT && postStatus != PostStatus.PUBLISHED)
			throw new IllegalArgumentException("등록할 때 게시 상태는 DRAFT 또는 PUBLISHED 만 가능합니다.");
		LocalDateTime now = now();
		Long me = CurrentUser.idOrNull();
		String title = r.title().trim();
		String category = blankToNull(r.inquiryCategory());
		Board b = new Board(r.type(), title, r.content(), me, postStatus, now);

		if (r.type() == BoardType.INQUIRY) {
			prepareInquiry(b, r, category);
		} else {
			if (r.memberId() != null || hasText(r.guestName()) || hasText(r.guestPhone()) || hasText(r.guestEmail())
					|| r.reservationId() != null)
				throw new IllegalArgumentException("고객 정보·예약 연결은 고객 문의(INQUIRY)에만 넣을 수 있습니다.");
			b.edit(title, r.content(), r.visibility() == null ? BoardVisibility.PUBLIC : r.visibility(),
					Boolean.TRUE.equals(r.pinned()), r.displayOrder() == null ? 0 : r.displayOrder(),
					r.publishStartAt(), r.publishEndAt(), r.eventStartAt(), r.eventEndAt(), category, now);
			String answer = blankToNull(r.answerContent());
			if (answer != null) {
				if (r.type() != BoardType.FAQ)
					throw new IllegalArgumentException("등록과 함께 답변을 넣을 수 있는 글은 FAQ 뿐입니다.");
				b.answer(r.answerContent().strip(), me, now);
			}
		}
		repo.save(b);
		if (r.files() != null && !r.files().isEmpty())
			replaceAttachments(b, r.files(), now);
		return detail(b);
	}

	/**
	 * 내용·게시 설정 수정. 고객 문의는 고객이 쓴 내용을 직원이 고칠 수 없으므로 분류(inquiryCategory)만 바꾼다.
	 */
	public BoardDetail update(long id, BoardUpdateRequest r) {
		Board b = findForUpdate(id);
		LocalDateTime now = now();
		String category = blankToNull(r.inquiryCategory());
		if (b.getType() == BoardType.INQUIRY) {
			boolean other = (r.title() != null && !r.title().trim().equals(b.getTitle()))
					|| (r.content() != null && !r.content().equals(b.getContent()))
					|| (r.visibility() != null && r.visibility() != b.getVisibility())
					|| (r.pinned() != null && r.pinned() != b.isPinned())
					|| (r.displayOrder() != null && r.displayOrder() != b.getDisplayOrder())
					|| r.publishStartAt() != null || r.publishEndAt() != null || r.eventStartAt() != null
					|| r.eventEndAt() != null;
			if (other)
				throw new IllegalArgumentException("고객 문의는 분류(inquiryCategory)만 수정할 수 있습니다.");
			b.changeCategory(category, now);
		} else {
			if (!hasText(r.title()) || !hasText(r.content()))
				throw new IllegalArgumentException("제목과 내용을 입력하세요.");
			b.edit(r.title().trim(), r.content(), r.visibility() == null ? b.getVisibility() : r.visibility(),
					r.pinned() == null ? b.isPinned() : r.pinned(),
					r.displayOrder() == null ? b.getDisplayOrder() : r.displayOrder(), r.publishStartAt(),
					r.publishEndAt(), r.eventStartAt(), r.eventEndAt(), category, now);
		}
		return detail(b);
	}

	public BoardDetail changePostStatus(long id, PostStatus status) {
		Board b = findForUpdate(id);
		PostStatus before = b.changePostStatus(status, now());
		if (before != status)
			audit.log("STATUS_CHANGE", TABLE, id, Map.of("field", "post_status", "before", before, "after", status));
		return detail(b);
	}

	/** 답변 등록·수정(FAQ·INQUIRY). 문의는 ANSWERED 가 된다. */
	public BoardDetail answer(long id, String answerContent) {
		Board b = findForUpdate(id);
		InquiryStatus before = b.getInquiryStatus();
		boolean isNew = b.answer(answerContent.strip(), CurrentUser.idOrNull(), now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("boardType", b.getType());
		if (before != null) {
			details.put("inquiryStatusBefore", before);
			details.put("inquiryStatusAfter", b.getInquiryStatus());
		}
		audit.log(isNew ? "ANSWER" : "ANSWER_UPDATE", TABLE, id, details);
		return detail(b);
	}

	/** 답변 삭제 → WAITING (문의는 IN_PROGRESS) */
	public BoardDetail deleteAnswer(long id) {
		Board b = findForUpdate(id);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("boardType", b.getType());
		details.put("answeredBy", b.getAnsweredBy());
		details.put("answeredAt", b.getAnsweredAt() == null ? null : b.getAnsweredAt().toString());
		b.clearAnswer(now());
		audit.log("ANSWER_DELETE", TABLE, id, details);
		return detail(b);
	}

	/** 문의 처리 상태 변경(INQUIRY). 같은 상태면 그대로 둔다. */
	public BoardDetail changeInquiryStatus(long id, InquiryStatus status) {
		Board b = findForUpdate(id);
		InquiryStatus before = b.getInquiryStatus();
		if (b.changeInquiryStatus(status, now()))
			audit.log("STATUS_CHANGE", TABLE, id,
					Map.of("field", "inquiry_status", "before", before, "after", status));
		return detail(b);
	}

	/**
	 * 첨부 전체 교체. 파일은 있어야 하고 ACTIVE 여야 한다. 같은 키를 지웠다 다시 넣지 않도록(Hibernate 는 insert 를 delete 보다
	 * 먼저 실행) 기존 행은 수정, 빠진 행은 삭제, 새 행만 추가한다.
	 */
	public BoardDetail replaceFiles(long id, List<AttachmentRequest> attachments) {
		Board b = findForUpdate(id);
		LocalDateTime now = now();
		b.touch(now);
		replaceAttachments(b, attachments, now);
		return detail(b);
	}

	/** 삭제 표시(deleted_at) + post_status CLOSED. 첨부·답변은 이력으로 남긴다. */
	public void delete(long id) {
		Board b = findForUpdate(id);
		PostStatus before = b.getPostStatus();
		b.delete(now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("boardType", b.getType());
		details.put("title", b.getTitle());
		details.put("postStatusBefore", before);
		audit.log("DELETE", TABLE, id, details);
	}

	@Transactional(readOnly = true)
	public InquirySummary inquirySummary() {
		Map<InquiryStatus, Long> m = new EnumMap<>(InquiryStatus.class);
		for (Object[] row : repo.countByInquiryStatus(BoardType.INQUIRY))
			if (row[0] != null)
				m.put((InquiryStatus) row[0], ((Number) row[1]).longValue());
		long received = m.getOrDefault(InquiryStatus.RECEIVED, 0L);
		long inProgress = m.getOrDefault(InquiryStatus.IN_PROGRESS, 0L);
		long answered = m.getOrDefault(InquiryStatus.ANSWERED, 0L);
		long closed = m.getOrDefault(InquiryStatus.CLOSED, 0L);
		return new InquirySummary(received, inProgress, answered, closed, received + inProgress + answered + closed);
	}

	/** 직원이 고객 대신 등록하는 문의(전화 문의 등) */
	private void prepareInquiry(Board b, BoardCreateRequest r, String category) {
		if (Boolean.TRUE.equals(r.pinned()) || (r.displayOrder() != null && r.displayOrder() != 0))
			throw new IllegalArgumentException("고객 문의는 상단 고정·정렬 순서를 쓸 수 없습니다.");
		if (r.publishStartAt() != null || r.publishEndAt() != null || r.eventStartAt() != null
				|| r.eventEndAt() != null)
			throw new IllegalArgumentException("고객 문의에는 게시·행사 기간을 넣을 수 없습니다.");
		if (hasText(r.answerContent()))
			throw new IllegalArgumentException("문의 답변은 등록 후 답변 API(POST /api/boards/{id}/answer)로 등록하세요.");

		Long memberId = r.memberId();
		String guestName = blankToNull(r.guestName());
		String guestPhone = blankToNull(r.guestPhone());
		String guestEmail = blankToNull(r.guestEmail());
		if (memberId != null) {
			if (guestName != null || guestPhone != null || guestEmail != null)
				throw new IllegalArgumentException("회원 문의에는 비회원 정보(guestName·guestPhone·guestEmail)를 넣지 않습니다.");
			refs.require(Ref.MEMBER, memberId);
		} else {
			if (guestName == null)
				throw new IllegalArgumentException("문의에는 회원(memberId) 또는 비회원 이름(guestName)이 필요합니다.");
			if (guestPhone == null && guestEmail == null)
				throw new IllegalArgumentException("비회원 문의는 답변받을 연락처(guestPhone 또는 guestEmail)가 필요합니다.");
		}
		if (r.reservationId() != null) {
			Long owner = lookup.reservationMemberId(r.reservationId());
			if (owner == null)
				throw NotFoundException.of(Ref.RESERVATION.label, r.reservationId());
			if (memberId != null && !memberId.equals(owner))
				throw new IllegalArgumentException("연결한 예약의 회원과 문의한 회원이 다릅니다.");
		}
		b.registerInquiry(memberId, guestName, guestPhone, guestEmail, r.reservationId(), category);
	}

	private void replaceAttachments(Board b, List<AttachmentRequest> requested, LocalDateTime now) {
		List<AttachmentRequest> list = requested == null ? List.of() : requested;
		if (list.size() > BoardDtos.MAX_ATTACHMENTS)
			throw new IllegalArgumentException("첨부 파일은 " + BoardDtos.MAX_ATTACHMENTS + "개까지 붙일 수 있습니다.");
		Set<Long> ids = new HashSet<>();
		for (AttachmentRequest a : list) {
			if (a == null || a.fileId() == null)
				throw new IllegalArgumentException("첨부 파일 id(fileId)가 필요합니다.");
			if (!ids.add(a.fileId()))
				throw new IllegalArgumentException("같은 파일을 두 번 첨부할 수 없습니다: " + a.fileId());
		}

		Map<Long, String> statuses = lookup.lockFileStatuses(ids);
		for (Long fileId : ids) {
			String status = statuses.get(fileId);
			if (status == null)
				throw NotFoundException.of(Ref.COMMON_FILE.label, fileId);
			if (!"ACTIVE".equals(status))
				throw new ConflictException("첨부할 수 없는 파일입니다(상태: " + status + "): " + fileId);
		}

		Map<Long, BoardFile> existing = new HashMap<>();
		for (BoardFile f : files.findByBoardId(b.getId()))
			existing.put(f.getCommonFileId(), f);
		List<BoardFile> removed = new ArrayList<>();
		for (BoardFile f : existing.values())
			if (!ids.contains(f.getCommonFileId()))
				removed.add(f);
		files.deleteAll(removed);

		List<BoardFile> added = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			AttachmentRequest a = list.get(i);
			short order = (short) (a.displayOrder() == null ? i : a.displayOrder());
			String caption = blankToNull(a.caption());
			BoardFile current = existing.get(a.fileId());
			if (current != null)
				current.update(order, caption);
			else
				added.add(new BoardFile(b.getId(), a.fileId(), order, caption, now));
		}
		files.saveAll(added);
		files.flush();
	}

	private BoardDetail detail(Board b) {
		Map<Long, String> admins = lookup.adminNames(
				Stream.of(b.getAuthorAdminUserId(), b.getAnsweredBy()).filter(Objects::nonNull).toList());
		String memberName = b.getAuthorMemberId() == null ? null
				: lookup.memberNames(List.of(b.getAuthorMemberId())).get(b.getAuthorMemberId());
		String authorAdminName = admins.get(b.getAuthorAdminUserId());
		return BoardDetail.of(b, Author.of(b, memberName, authorAdminName), authorAdminName,
				admins.get(b.getAnsweredBy()), lookup.attachments(b.getId()));
	}

	private Board find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("게시글", id));
	}

	private Board findForUpdate(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("게시글", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
