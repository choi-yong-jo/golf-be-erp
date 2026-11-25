package com.golf.erp.member;

import com.golf.erp.member.dto.RestrictionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members/{memberId}/restrictions")
public class ReservationRestrictionController {
	private final ReservationRestrictionService service;

	public ReservationRestrictionController(ReservationRestrictionService service) {
		this.service = service;
	}

	@GetMapping
	public List<ReservationRestriction> list(@PathVariable long memberId) {
		return service.list(memberId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationRestriction add(@PathVariable long memberId, @Valid @RequestBody RestrictionRequest r) {
		return service.add(memberId, r);
	}

	@PostMapping("/{restrictionId}/release")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void release(@PathVariable long memberId, @PathVariable long restrictionId) {
		service.release(memberId, restrictionId);
	}
}
