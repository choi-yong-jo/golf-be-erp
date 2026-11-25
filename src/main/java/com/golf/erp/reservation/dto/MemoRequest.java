package com.golf.erp.reservation.dto;

import jakarta.validation.constraints.Size;

public record MemoRequest(@Size(max = 500) String memo) {
}
