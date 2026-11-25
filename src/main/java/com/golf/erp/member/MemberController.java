package com.golf.erp.member;

import com.golf.erp.common.PageResult;
import com.golf.erp.member.dto.MemberRequest;
import com.golf.erp.member.dto.MemberStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
public class MemberController {
	private final MemberService service;

	public MemberController(MemberService service) {
		this.service = service;
	}

	@GetMapping
	public PageResult<Member> search(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) MemberStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.search(keyword, status, page, size);
	}

	@GetMapping("/{id}")
	public Member get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Member create(@Valid @RequestBody MemberRequest r) {
		return service.create(r);
	}

	@PutMapping("/{id}")
	public Member update(@PathVariable long id, @Valid @RequestBody MemberRequest r) {
		return service.update(id, r);
	}

	/** 삭제 대신 상태 변경(ACTIVE/SUSPENDED/WITHDRAWN) */
	@PatchMapping("/{id}/status")
	public Member changeStatus(@PathVariable long id, @Valid @RequestBody MemberStatusRequest r) {
		return service.changeStatus(id, r.status());
	}
}
