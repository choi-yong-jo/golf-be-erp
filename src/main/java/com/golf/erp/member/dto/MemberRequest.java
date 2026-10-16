package com.golf.erp.member.dto;
import jakarta.validation.constraints.*;
public record MemberRequest(@NotBlank String memberNo,@NotBlank String name,String phone,@Email String email,String status) {}
