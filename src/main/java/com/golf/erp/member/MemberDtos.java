package com.golf.erp.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 회원·예약 제한 API 요청/응답 */
public final class MemberDtos {
	private MemberDtos() {
	}

	/**
	 * 회원 등록/수정(오프라인 회원: 로그인 계정 없음). memberNo 를 비우면 등록 시 MB-yyyyMMdd-{id} 로 자동 발급한다
	 * (수정 시 비우면 기존 번호 유지). grade 생략 시 GENERAL, marketingConsent 생략 시 false.
	 */
	public record MemberRequest(@Size(max = 30) String memberNo, @NotBlank @Size(max = 100) String name,
			@Size(max = 30) String phone, @Email @Size(max = 255) String email, @Past LocalDate birthDate,
			@Size(max = 20) String gender, @Size(max = 500) String address, @Size(max = 30) String grade,
			Boolean marketingConsent) {
	}

	public record MemberStatusRequest(@NotNull MemberStatus status, @Size(max = 500) String reason) {
	}

	/** online: 예약 앱에서 가입한(로그인 계정이 있는) 회원 */
	public record MemberResponse(Long id, String memberNo, String loginId, boolean online, String name, String phone,
			String email, MemberStatus status, String grade, LocalDate birthDate, String gender, String address,
			boolean marketingConsent, LocalDateTime createdAt, LocalDateTime updatedAt) {

		static MemberResponse of(Member m) {
			return new MemberResponse(m.getId(), m.getMemberNo(), m.getLoginId(), m.isOnline(), m.getName(),
					m.getPhone(), m.getEmail(), m.getStatus(), m.getGrade(), m.getBirthDate(), m.getGender(),
					m.getAddress(), m.isMarketingConsent(), m.getCreatedAt(), m.getUpdatedAt());
		}
	}

	/** 회원의 예약 내역 한 줄(예약 앱 예약 포함) */
	public record MemberReservationRow(Long reservationId, String reservationNo, Long teeTimeId, Long golfCourseId,
			String golfCourseName, LocalDateTime playAt, int playerCount, String status, BigDecimal totalAmount,
			LocalDateTime reservedAt, LocalDateTime cancelledAt) {
	}

	/** startsAt 생략 시 지금부터, endsAt 생략 시 무기한 */
	public record RestrictionRequest(@NotBlank @Size(max = 30) String restrictionType, LocalDateTime startsAt,
			LocalDateTime endsAt, @NotBlank @Size(max = 1000) String reason) {
	}

	/** active: 지금 적용 중인 제한인지 */
	public record RestrictionResponse(Long id, Long memberId, String restrictionType, LocalDateTime startsAt,
			LocalDateTime endsAt, String reason, boolean active, Long createdBy, LocalDateTime createdAt) {

		static RestrictionResponse of(ReservationRestriction r, LocalDateTime now) {
			return new RestrictionResponse(r.getId(), r.getMemberId(), r.getRestrictionType(), r.getStartsAt(),
					r.getEndsAt(), r.getReason(), r.isActiveAt(now), r.getCreatedBy(), r.getCreatedAt());
		}
	}
}
