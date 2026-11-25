package com.golf.erp.teetime;

import com.golf.erp.redis.TeeTimeSseHub;
import com.golf.erp.teetime.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tee-times")
public class TeeTimeController {
	private final TeeTimeService service;
	private final TeeTimeSseHub hub;

	public TeeTimeController(TeeTimeService service, TeeTimeSseHub hub) {
		this.service = service;
		this.hub = hub;
	}

	/**
	 * 예약 현황 실시간 알림(SSE). 연결 직후 "connected", 이후 티타임/예약이 바뀔 때마다 "tee-time" 이벤트(TeeTimeEvent).
	 * 화면은 이벤트의 golfCourseId·playDate 가 보고 있는 범위면 GET /api/tee-times 를 다시 조회한다.
	 * courseId 를 주면 그 코스 이벤트만 받는다.
	 */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream(@RequestParam(required = false) Long courseId) {
		return hub.subscribe(courseId);
	}

	/** 예약 현황: 기간(최대 31일)의 티타임과 활성 예약. to 생략 시 from 하루 */
	@GetMapping
	public TeeTimeBoard board(@RequestParam(required = false) Long courseId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) TeeTimeStatus status) {
		return service.board(courseId, from, to == null ? from : to, status);
	}

	@GetMapping("/{id}")
	public TeeTime get(@PathVariable long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TeeTime create(@Valid @RequestBody TeeTimeRequest r) {
		return service.create(r);
	}

	/** 기간 일괄 생성. 이미 있는 슬롯과 휴장 구간은 건너뛴다. */
	@PostMapping("/generate")
	@ResponseStatus(HttpStatus.CREATED)
	public TeeTimeGenerateResult generate(@Valid @RequestBody TeeTimeGenerateRequest r) {
		return service.generate(r);
	}

	@PatchMapping("/{id}")
	public TeeTime update(@PathVariable long id, @Valid @RequestBody TeeTimeUpdateRequest r) {
		return service.update(id, r);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}
}
