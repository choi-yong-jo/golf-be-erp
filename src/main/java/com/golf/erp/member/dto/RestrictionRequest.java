package com.golf.erp.member.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** startsAt 생략 시 지금부터, endsAt 생략 시 무기한 */
public record RestrictionRequest(@NotBlank @Size(max = 30) String restrictionType, LocalDateTime startsAt,
		LocalDateTime endsAt, @NotBlank @Size(max = 1000) String reason) {
}
