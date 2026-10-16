package com.golf.erp.dashboard;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
	private final DashboardService service;

	public DashboardController(DashboardService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	public DashboardSummary summary() {
		return service.summary();
	}

	@GetMapping("/recent-reservations")
	public Map<String, Object> recent(@RequestParam(defaultValue = "5") int limit) {
		return Map.of("items", service.recent(limit));
	}
}
