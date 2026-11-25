package com.golf.erp.reservation.dto;

import jakarta.validation.constraints.Size;

/** reason 생략 시 "관리자 취소" */
public record CancelRequest(@Size(max = 500) String reason) {
}
