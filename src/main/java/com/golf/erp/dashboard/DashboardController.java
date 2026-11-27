package com.golf.erp.dashboard;

import com.golf.erp.dashboard.DashboardDtos.DashboardSummary;
import com.golf.erp.dashboard.DashboardDtos.RecentReservation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 대시보드. 로그인한 직원이면 누구나 볼 수 있다(ErpModule 미등록 경로). */
@Tag(name = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
	private final DashboardService service;

	public DashboardController(DashboardService service) {
		this.service = service;
	}

	@Operation(summary = "운영 현황 요약", description = "date 생략 시 오늘. 그날 예약(상태별)·티타임 점유·라운드·매출 + 최근 예약")
	@GetMapping("/summary")
	public DashboardSummary summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(defaultValue = "5") int recentLimit) {
		return service.summary(date, recentLimit);
	}

	@Operation(summary = "최근 예약", description = "등록 순 최근 예약(limit 1~50)")
	@GetMapping("/recent-reservations")
	public List<RecentReservation> recent(@RequestParam(defaultValue = "5") int limit) {
		return service.recent(limit);
	}
}
