package com.golf.erp.course.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** closureType 기본값 CLOSED. closureStart 포함, closureEnd 미포함 */
public record ClosureRequest(@NotNull LocalDateTime closureStart, @NotNull LocalDateTime closureEnd,
		@Size(max = 20) String closureType, @NotBlank @Size(max = 500) String reason) {
}
