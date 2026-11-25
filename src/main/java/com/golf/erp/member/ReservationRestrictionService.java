package com.golf.erp.member;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.member.dto.RestrictionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ReservationRestrictionService {
	private final ReservationRestrictionRepository repo;
	private final MemberService memberService;
	private final Clock clock;

	public ReservationRestrictionService(ReservationRestrictionRepository repo, MemberService memberService,
			Clock clock) {
		this.repo = repo;
		this.memberService = memberService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ReservationRestriction> list(long memberId) {
		memberService.get(memberId);
		return repo.findByMemberIdOrderByStartsAtDesc(memberId);
	}

	public ReservationRestriction add(long memberId, RestrictionRequest r) {
		memberService.get(memberId);
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime starts = r.startsAt() == null ? now : r.startsAt();
		if (r.endsAt() != null && !r.endsAt().isAfter(starts))
			throw new IllegalArgumentException("제한 종료 시각은 시작 시각 이후여야 합니다.");
		return repo.save(new ReservationRestriction(memberId, r.restrictionType().trim(), starts, r.endsAt(),
				r.reason(), now));
	}

	/** 제한 해제: 진행 중이면 지금 종료, 시작 전이면 행 삭제 */
	public void release(long memberId, long restrictionId) {
		ReservationRestriction r = repo.findById(restrictionId).filter(x -> x.getMemberId().equals(memberId))
				.orElseThrow(() -> new NotFoundException("예약 제한을 찾을 수 없습니다: " + restrictionId));
		LocalDateTime now = LocalDateTime.now(clock);
		if (r.getEndsAt() != null && !r.getEndsAt().isAfter(now))
			throw new ConflictException("이미 종료된 제한입니다.");
		if (r.getStartsAt().isAfter(now))
			repo.delete(r);
		else
			r.endAt(now.isAfter(r.getStartsAt()) ? now : r.getStartsAt().plusSeconds(1));
	}

	@Transactional(readOnly = true)
	public List<ReservationRestriction> activeAt(long memberId, LocalDateTime at) {
		return repo.findActive(memberId, at);
	}
}
