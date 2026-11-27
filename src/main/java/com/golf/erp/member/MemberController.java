package com.golf.erp.member;

import com.golf.erp.common.PageResult;
import com.golf.erp.member.MemberDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/members")
public class MemberController {
	private final MemberService members;
	private final ReservationRestrictionService restrictions;

	public MemberController(MemberService members, ReservationRestrictionService restrictions) {
		this.members = members;
		this.restrictions = restrictions;
	}

	@Operation(summary = "회원 목록", description = "keyword: 이름·회원번호·전화·이메일·로그인 ID. grade: 회원 등급(예: GENERAL)")
	@GetMapping
	public PageResult<MemberResponse> list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) MemberStatus status, @RequestParam(required = false) String grade,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return members.list(keyword, status, grade, page, size);
	}

	@GetMapping("/{id}")
	public MemberResponse get(@PathVariable long id) {
		return members.get(id);
	}

	@Operation(summary = "회원 등록(오프라인 회원)", description = "로그인 계정 없이 등록한다. memberNo 생략 시 MB-yyyyMMdd-{id} 자동 발급")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponse create(@Valid @RequestBody MemberRequest r) {
		return members.create(r);
	}

	@Operation(summary = "회원 수정", description = "memberNo 를 비우면 기존 번호 유지")
	@PutMapping("/{id}")
	public MemberResponse update(@PathVariable long id, @Valid @RequestBody MemberRequest r) {
		return members.update(id, r);
	}

	@Operation(summary = "회원 상태 변경", description = "ACTIVE, SUSPENDED, WITHDRAWN (삭제 대신 사용)")
	@PatchMapping("/{id}/status")
	public MemberResponse changeStatus(@PathVariable long id, @Valid @RequestBody MemberStatusRequest r) {
		return members.changeStatus(id, r.status(), r.reason());
	}

	@Operation(summary = "회원 예약 내역", description = "예약 앱에서 만든 예약 포함, 플레이 일시 최근 순")
	@GetMapping("/{id}/reservations")
	public PageResult<MemberReservationRow> reservations(@PathVariable long id,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return members.reservations(id, status, page, size);
	}

	@Operation(summary = "회원 예약 제한 목록")
	@GetMapping("/{id}/restrictions")
	public List<RestrictionResponse> restrictions(@PathVariable long id) {
		return restrictions.list(id);
	}

	@Operation(summary = "예약 제한 등록", description = "startsAt 생략 시 지금부터, endsAt 생략 시 무기한")
	@PostMapping("/{id}/restrictions")
	@ResponseStatus(HttpStatus.CREATED)
	public RestrictionResponse createRestriction(@PathVariable long id, @Valid @RequestBody RestrictionRequest r) {
		return restrictions.create(id, r);
	}

	@Operation(summary = "예약 제한 종료", description = "진행 중이면 지금 종료, 시작 전이면 삭제. 이미 끝난 제한이면 409")
	@PostMapping("/{id}/restrictions/{restrictionId}/end")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void endRestriction(@PathVariable long id, @PathVariable long restrictionId) {
		restrictions.end(id, restrictionId);
	}
}
