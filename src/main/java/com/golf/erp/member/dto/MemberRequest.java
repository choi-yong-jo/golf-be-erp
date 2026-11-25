package com.golf.erp.member.dto;

import jakarta.validation.constraints.*;

/** 회원 등록/수정. memberNo 는 선택(예약 앱 가입 회원은 없음), 이메일은 소문자로 저장한다. */
public record MemberRequest(@Size(max = 30) String memberNo, @NotBlank @Size(max = 100) String memberName,
		@Size(max = 30) String phone, @Email @Size(max = 255) String email) {
}
