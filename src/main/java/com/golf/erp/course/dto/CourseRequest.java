package com.golf.erp.course.dto;

import jakarta.validation.constraints.*;

public record CourseRequest(@NotBlank @Size(max = 30) String courseCode, @NotBlank @Size(max = 200) String courseName,
		@Size(max = 500) String address, @Size(max = 30) String phone, @Min(1) @Max(72) Integer holeCount) {
}
