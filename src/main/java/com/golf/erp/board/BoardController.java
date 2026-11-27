package com.golf.erp.board;

import com.golf.erp.board.BoardDtos.*;
import com.golf.erp.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "게시판")
@RestController
@RequestMapping("/api/boards")
public class BoardController {
	private final BoardService service;

	public BoardController(BoardService service) {
		this.service = service;
	}

	@Operation(summary = "게시글 목록", description = "정렬: 상단 고정 → displayOrder → 최신순. keyword: 제목·내용, from/to: 등록일, "
			+ "includeDeleted=true 면 삭제된 글 포함")
	@GetMapping
	public PageResult<BoardRow> list(@RequestParam(required = false) BoardType type,
			@RequestParam(required = false) PostStatus postStatus,
			@RequestParam(required = false) BoardVisibility visibility,
			@RequestParam(required = false) InquiryStatus inquiryStatus,
			@RequestParam(required = false) AnswerStatus answerStatus, @RequestParam(required = false) Boolean pinned,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "false") boolean includeDeleted, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(type, postStatus, visibility, inquiryStatus, answerStatus, pinned, keyword, from, to,
				includeDeleted, page, size);
	}

	@Operation(summary = "고객 문의 현황", description = "삭제되지 않은 문의의 처리 상태별 건수(대시보드용)")
	@GetMapping("/inquiries/summary")
	public InquirySummary inquirySummary() {
		return service.inquirySummary();
	}

	@Operation(summary = "게시글 상세", description = "첨부·답변 포함. ERP 조회는 조회수를 올리지 않는다")
	@GetMapping("/{id}")
	public BoardDetail get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "게시글 등록", description = "NOTICE/EVENT/FAQ: 직원 작성(FAQ 는 answerContent 로 답변까지 등록 가능). "
			+ "INQUIRY: 고객 문의 대리 등록 — memberId 또는 guestName(+연락처) 필요, 항상 비공개(PRIVATE). "
			+ "postStatus: DRAFT 또는 PUBLISHED(기본). files: 업로드한 파일 첨부(선택)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BoardDetail create(@Valid @RequestBody BoardCreateRequest r) {
		return service.create(r);
	}

	@Operation(summary = "게시글 수정", description = "visibility·pinned·displayOrder 는 null 이면 유지, 기간·분류는 null 이면 삭제. "
			+ "고객 문의(INQUIRY)는 inquiryCategory 만 변경 가능. 삭제된 글은 409")
	@PutMapping("/{id}")
	public BoardDetail update(@PathVariable long id, @Valid @RequestBody BoardUpdateRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "게시 상태 변경", description = "DRAFT, PUBLISHED, HIDDEN, CLOSED (고객 문의는 DRAFT 불가)")
	@PatchMapping("/{id}/status")
	public BoardDetail changeStatus(@PathVariable long id, @Valid @RequestBody PostStatusRequest r) {
		return service.changePostStatus(id, r.status());
	}

	@Operation(summary = "답변 등록·수정", description = "FAQ·INQUIRY 만. answer_status ANSWERED, 문의는 inquiry_status ANSWERED. "
			+ "이미 답변이 있으면 수정. 종료(CLOSED)된 문의는 409")
	@PostMapping("/{id}/answer")
	public BoardDetail answer(@PathVariable long id, @Valid @RequestBody AnswerRequest r) {
		return service.answer(id, r.answerContent());
	}

	@Operation(summary = "답변 삭제", description = "답변 대기(WAITING)로 되돌림. 문의는 처리 중(IN_PROGRESS)")
	@DeleteMapping("/{id}/answer")
	public BoardDetail deleteAnswer(@PathVariable long id) {
		return service.deleteAnswer(id);
	}

	@Operation(summary = "문의 처리 상태 변경", description = "INQUIRY 만. RECEIVED → IN_PROGRESS/ANSWERED/CLOSED, "
			+ "IN_PROGRESS → ANSWERED/CLOSED, ANSWERED → IN_PROGRESS/CLOSED, CLOSED → IN_PROGRESS/ANSWERED. "
			+ "ANSWERED 는 답변이 있어야 한다")
	@PatchMapping("/{id}/inquiry-status")
	public BoardDetail changeInquiryStatus(@PathVariable long id, @Valid @RequestBody InquiryStatusRequest r) {
		return service.changeInquiryStatus(id, r.status());
	}

	@Operation(summary = "첨부 교체", description = "{files: [{fileId, displayOrder, caption}]} 로 첨부 전체를 바꾼다. "
			+ "파일은 ACTIVE 여야 한다. 빈 목록이면 첨부 모두 해제")
	@PutMapping("/{id}/files")
	public BoardDetail replaceFiles(@PathVariable long id, @Valid @RequestBody AttachmentsRequest r) {
		return service.replaceFiles(id, r.files());
	}

	@Operation(summary = "게시글 삭제", description = "삭제 표시(deleted_at) + 게시 상태 CLOSED. 첨부·답변은 남긴다")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}
}
