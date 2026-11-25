package com.golf.erp.settlement;

import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class SettlementService {
	private final SettlementRepository repo;
	private final SettlementMapper mapper;
	private final Clock clock;

	public SettlementService(SettlementRepository repo, SettlementMapper mapper, Clock clock) {
		this.repo = repo;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Settlement> list(LocalDate from, LocalDate to) {
		if (to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일 이후여야 합니다.");
		if (ChronoUnit.DAYS.between(from, to) > 366)
			throw new IllegalArgumentException("조회 기간은 최대 1년입니다.");
		return repo.findBySettlementDateBetweenOrderBySettlementDateDesc(from, to);
	}

	@Transactional(readOnly = true)
	public Settlement get(LocalDate date) {
		return repo.findBySettlementDate(date).orElseThrow(() -> new NotFoundException("정산 내역이 없습니다: " + date));
	}

	/** 결제 테이블에서 해당 일자 금액을 다시 집계한다. 마감(CLOSED)된 날짜는 재계산하지 않는다. */
	public Settlement calculate(LocalDate date) {
		LocalDate today = LocalDate.now(clock);
		if (date.isAfter(today))
			throw new IllegalArgumentException("미래 날짜는 정산할 수 없습니다.");
		Settlement s = repo.findBySettlementDate(date)
				.orElseGet(() -> new Settlement(date, LocalDateTime.now(clock)));
		if (s.isClosed())
			throw new ConflictException("마감된 정산입니다. 재계산하려면 먼저 마감을 해제하세요.");
		LocalDateTime from = date.atStartOfDay();
		LocalDateTime to = date.plusDays(1).atStartOfDay();
		s.apply(mapper.sumGross(from, to), mapper.sumRefund(from, to));
		return repo.save(s);
	}

	/** 마감. 당일은 결제가 더 생길 수 있어 마감할 수 없다. */
	public Settlement close(LocalDate date) {
		if (!date.isBefore(LocalDate.now(clock)))
			throw new IllegalArgumentException("지난 날짜만 마감할 수 있습니다.");
		Settlement s = calculate(date);
		s.close();
		return s;
	}

	public Settlement reopen(LocalDate date) {
		Settlement s = get(date);
		if (!s.isClosed())
			throw new ConflictException("마감되지 않은 정산입니다.");
		s.reopen();
		return s;
	}
}
