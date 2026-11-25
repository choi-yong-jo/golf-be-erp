package com.golf.erp.course.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** courseStatus: OPEN / CLOSED, active=false 면 목록에서 비활성(미사용) 처리 */
public record CourseStatusRequest(@NotNull @Pattern(regexp = "OPEN|CLOSED") String courseStatus,
		@NotNull Boolean active) {
}
