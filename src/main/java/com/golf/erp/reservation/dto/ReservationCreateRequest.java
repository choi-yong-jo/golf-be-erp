package com.golf.erp.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** companions: 예약자 본인을 뺀 동반자(최대 playerCount-1명). 예약자는 1번 플레이어로 자동 등록된다. */
public record ReservationCreateRequest(@NotNull Long teeTimeId, @NotNull Long memberId,
		@NotNull @Min(1) @Max(8) Integer playerCount, @Size(max = 500) String memo,
		@Valid @Size(max = 7) List<PlayerRequest> companions) {

	public record PlayerRequest(@NotBlank @Size(max = 100) String playerName, @Size(max = 30) String phone,
			Long memberId) {
	}
}
