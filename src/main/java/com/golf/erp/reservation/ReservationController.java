package com.golf.erp.reservation;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
	private final ReservationService service;

	public ReservationController(ReservationService service) {
		this.service = service;
	}

	@GetMapping
	public Map<String, Object> search(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.search(keyword, status, page, size);
	}
}
