package com.golf.erp.member;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.member.MemberDtos.RestrictionRequest;
import com.golf.erp.member.MemberDtos.RestrictionResponse;
import com.golf.erp.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 회원별 예약 제한. 진행 중인 제한이 있으면 ERP·예약 앱 모두 예약 생성을 막는다. */
@Service
@Transactional
public class ReservationRestrictionService {
	private final ReservationRestrictionRepository repo;
	private final MemberService members;
	private final AuditLogger audit;
	private final Clock clock;

	public ReservationRestrictionService(ReservationRestrictionRepository repo, MemberService members,
			AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.members = members;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<RestrictionResponse> list(long memberId) {
		members.find(memberId);
		LocalDateTime now = now();
		return repo.findByMemberIdOrderByStartsAtDescIdDesc(memberId).stream()
				.map(r -> RestrictionResponse.of(r, now)).toList();
	}

	/** startsAt 생략 시 지금부터, endsAt 생략 시 무기한. restrictionType 은 대문자로 저장한다. */
	public RestrictionResponse create(long memberId, RestrictionRequest r) {
		members.find(memberId);
		LocalDateTime now = now();
		LocalDateTime starts = r.startsAt() == null ? now : r.startsAt();
		if (r.endsAt() != null && !r.endsAt().isAfter(now))
			throw new IllegalArgumentException("제한 종료 시각은 현재 이후여야 합니다.");
		ReservationRestriction saved = repo.save(new ReservationRestriction(memberId,
				r.restrictionType().trim().toUpperCase(Locale.ROOT), starts, r.endsAt(), r.reason().trim(),
				CurrentUser.idOrNull(), now));
		audit.log("CREATE", "reservation_restriction", saved.getId(),
				Map.of("memberId", memberId, "type", saved.getRestrictionType()));
		return RestrictionResponse.of(saved, now);
	}

	/**
	 * 제한 종료. 진행 중이면 지금 시각으로 끝내고, 아직 시작 전이면 효력이 없던 제한이므로 행을 지운다. 이미 끝난 제한이면 409.
	 */
	public void end(long memberId, long restrictionId) {
		ReservationRestriction r = repo.findByIdAndMemberId(restrictionId, memberId)
				.orElseThrow(() -> NotFoundException.of("예약 제한", restrictionId));
		LocalDateTime now = now();
		if (r.isFutureAt(now)) {
			repo.delete(r);
			audit.log("DELETE", "reservation_restriction", restrictionId, Map.of("memberId", memberId));
			return;
		}
		r.end(now);
		audit.log("END", "reservation_restriction", restrictionId, Map.of("memberId", memberId, "endsAt", r.getEndsAt()));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
