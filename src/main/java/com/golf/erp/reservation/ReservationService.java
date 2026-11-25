package com.golf.erp.reservation;

import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.course.GolfCourseRepository;
import com.golf.erp.course.GolfCourse;
import com.golf.erp.member.Member;
import com.golf.erp.member.MemberRepository;
import com.golf.erp.payment.PaymentRepository;
import com.golf.erp.reservation.dto.ReservationDetail;
import com.golf.erp.reservation.dto.ReservationRow;
import com.golf.erp.teetime.TeeTime;
import com.golf.erp.teetime.TeeTimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 예약 조회 */
@Service
@Transactional(readOnly = true)
public class ReservationService {
	private final ReservationMapper mapper;
	private final ReservationRepository repo;
	private final ReservationPlayerRepository playerRepo;
	private final ReservationHistoryRepository historyRepo;
	private final PaymentRepository paymentRepo;
	private final MemberRepository memberRepo;
	private final GolfCourseRepository courseRepo;
	private final TeeTimeRepository teeTimeRepo;

	public ReservationService(ReservationMapper mapper, ReservationRepository repo,
			ReservationPlayerRepository playerRepo, ReservationHistoryRepository historyRepo,
			PaymentRepository paymentRepo, MemberRepository memberRepo, GolfCourseRepository courseRepo,
			TeeTimeRepository teeTimeRepo) {
		this.mapper = mapper;
		this.repo = repo;
		this.playerRepo = playerRepo;
		this.historyRepo = historyRepo;
		this.paymentRepo = paymentRepo;
		this.memberRepo = memberRepo;
		this.courseRepo = courseRepo;
		this.teeTimeRepo = teeTimeRepo;
	}

	/** from/to: 플레이 날짜 기준(양 끝 포함) */
	public PageResult<ReservationRow> search(String keyword, ReservationStatus status, Long courseId, Long memberId,
			LocalDate from, LocalDate to, int page, int size) {
		PageResult.validate(page, size);
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
		String st = status == null ? null : status.name();
		LocalDateTime playFrom = from == null ? null : from.atStartOfDay();
		LocalDateTime playTo = to == null ? null : to.plusDays(1).atStartOfDay();
		return new PageResult<>(mapper.search(kw, st, courseId, memberId, playFrom, playTo, page * size, size), page,
				size, mapper.count(kw, st, courseId, memberId, playFrom, playTo));
	}

	public ReservationDetail detail(long id) {
		Reservation r = repo.findById(id).orElseThrow(() -> new NotFoundException("예약을 찾을 수 없습니다: " + id));
		Member member = memberRepo.findById(r.getMemberId()).orElse(null);
		TeeTime teeTime = r.getTeeTimeId() == null ? null : teeTimeRepo.findById(r.getTeeTimeId()).orElse(null);
		Long courseId = r.getGolfCourseId() != null ? r.getGolfCourseId()
				: teeTime == null ? null : teeTime.getGolfCourseId();
		String courseName = courseId == null ? null
				: courseRepo.findById(courseId).map(GolfCourse::getCourseName).orElse(null);
		return new ReservationDetail(r, member == null ? null : member.getMemberName(),
				member == null ? null : member.getPhone(), courseName, teeTime,
				playerRepo.findByReservationIdOrderByPlayerOrder(id),
				paymentRepo.findByReservationIdOrderByRequestedAtAscPaymentIdAsc(id),
				historyRepo.findByReservationIdOrderByCreatedAtAscReservationHistoryIdAsc(id));
	}
}
