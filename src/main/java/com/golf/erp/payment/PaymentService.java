package com.golf.erp.payment;

import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.payment.PaymentDtos.PaymentDetail;
import com.golf.erp.payment.PaymentDtos.PaymentResponse;
import com.golf.erp.payment.PaymentDtos.ReservationSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 예약 결제 내역 조회(예약 앱 온라인 결제 포함). 결제 등록은 POST /api/reservations/{id}/payments, 환불은 예약 취소
 * (POST /api/reservations/{id}/cancel)에서 함께 처리된다.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {
	private final PaymentRepository repo;
	private final JdbcTemplate jdbc;

	public PaymentService(PaymentRepository repo, JdbcTemplate jdbc) {
		this.repo = repo;
		this.jdbc = jdbc;
	}

	/**
	 * @param from 결제 요청일 시작(포함)
	 * @param to   결제 요청일 끝(포함)
	 */
	public PageResult<PaymentResponse> list(PaymentStatus status, PaymentType type, String method, String pgProvider,
			Long reservationId, LocalDate from, LocalDate to, int page, int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		Specification<Payment> spec = Specification.allOf(Specs.eq("status", status), Specs.eq("type", type),
				Specs.eq("method", method == null ? null : method.trim()),
				Specs.eq("pgProvider", pgProvider == null ? null : pgProvider.trim()),
				Specs.eq("reservationId", reservationId),
				Specs.gte("requestedAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("requestedAt", to == null ? null : to.plusDays(1).atStartOfDay()));
		Page<Payment> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt", "id")));
		Map<Long, ReservationSummary> reservations = reservations(
				result.getContent().stream().map(Payment::getReservationId).collect(Collectors.toSet()));
		return PageResult.of(result, p -> PaymentResponse.of(p, reservations.get(p.getReservationId())));
	}

	public PaymentDetail get(long id) {
		Payment p = repo.findById(id).orElseThrow(() -> NotFoundException.of("결제", id));
		ReservationSummary r = reservations(Set.of(p.getReservationId())).get(p.getReservationId());
		return new PaymentDetail(PaymentResponse.of(p, r), r);
	}

	/** 예약·회원 요약을 SQL 로 한 번에 읽는다(예약 모듈 엔티티를 참조하지 않음). */
	private Map<Long, ReservationSummary> reservations(Collection<Long> ids) {
		Map<Long, ReservationSummary> m = new HashMap<>();
		if (ids.isEmpty())
			return m;
		String placeholders = ids.stream().map(x -> "?").collect(Collectors.joining(","));
		jdbc.query("""
				SELECT r.reservation_id, r.reservation_no, r.reservation_status, r.member_id, mb.member_name, mb.phone,
				       r.total_amount, COALESCE(r.play_at, t.play_date + t.start_time)
				  FROM golf.reservation r
				  JOIN golf.member mb ON mb.member_id = r.member_id
				  LEFT JOIN golf.tee_time t ON t.tee_time_id = r.tee_time_id
				 WHERE r.reservation_id IN (""" + placeholders + ")", rs -> {
			long rid = rs.getLong(1);
			m.put(rid, new ReservationSummary(rid, rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5),
					rs.getString(6), rs.getBigDecimal(7), rs.getObject(8, LocalDateTime.class)));
		}, ids.toArray());
		return m;
	}
}
