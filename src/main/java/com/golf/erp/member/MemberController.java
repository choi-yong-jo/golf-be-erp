package com.golf.erp.member;

import com.golf.erp.member.dto.MemberRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {
	private final MemberService service;

	public MemberController(MemberService service) {
		this.service = service;
	}

	@GetMapping
	public List<Member> list() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public Member get(@PathVariable long id) {
		return service.findById(id);
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

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}
}
