package com.golf.erp.teetime;

import com.golf.erp.teetime.TeeTimeDtos.TeeTimeBoard;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeCreateRequest;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeGenerateRequest;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeGenerateResult;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeResponse;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeStatusRequest;
import com.golf.erp.teetime.TeeTimeDtos.TeeTimeUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "티타임")
@RestController
@RequestMapping("/api/tee-times")
public class TeeTimeController {
	private final TeeTimeService service;

	public TeeTimeController(TeeTimeService service) {
		this.service = service;
	}

	@Operation(summary = "티타임 예약 현황", description = "기간(최대 31일)의 티타임과 각 티타임의 활성 예약(예약자). "
			+ "to 생략 시 from 하루. course: 코스 코드(OUT, IN ...)")
	@GetMapping
	public TeeTimeBoard board(@RequestParam(required = false) Long golfCourseId,
			@RequestParam(required = false) String course,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) TeeTimeStatus status) {
		return service.board(golfCourseId, course, from, to == null ? from : to, status);
	}

	@GetMapping("/{id}")
	public TeeTimeResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "티타임 등록", description = "greenFee 생략 시 요금 정책으로 채운다. 휴장·중복 슬롯이면 409")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TeeTimeResponse create(@Valid @RequestBody TeeTimeCreateRequest r) {
		return service.create(r);
	}

	@Operation(summary = "티타임 일괄 생성", description = "기간(최대 62일)·시간대·간격으로 생성. 이미 있는 슬롯, 휴장 구간, "
			+ "지난 시각은 건너뛰고 건수를 돌려준다")
	@PostMapping("/generate")
	@ResponseStatus(HttpStatus.CREATED)
	public TeeTimeGenerateResult generate(@Valid @RequestBody TeeTimeGenerateRequest r) {
		return service.generate(r);
	}

	@Operation(summary = "티타임 수정", description = "홀 유형·정원·그린피. 활성 예약 인원보다 정원을 줄이면 409")
	@PutMapping("/{id}")
	public TeeTimeResponse update(@PathVariable long id, @Valid @RequestBody TeeTimeUpdateRequest r) {
		return service.update(id, r);
	}

	@Operation(summary = "티타임 상태 변경", description = "OPEN, BLOCKED, CLOSED (BOOKED 는 예약으로만). 활성 예약이 있으면 409")
	@PatchMapping("/{id}/status")
	public TeeTimeResponse changeStatus(@PathVariable long id, @Valid @RequestBody TeeTimeStatusRequest r) {
		return service.changeStatus(id, r);
	}

	@Operation(summary = "티타임 삭제", description = "예약·라운드 이력이 있으면 409(대신 CLOSED 로 변경)")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}
}
