package com.golf.erp.member.dto;

import com.golf.erp.member.MemberStatus;
import jakarta.validation.constraints.NotNull;

public record MemberStatusRequest(@NotNull MemberStatus status) {
}
