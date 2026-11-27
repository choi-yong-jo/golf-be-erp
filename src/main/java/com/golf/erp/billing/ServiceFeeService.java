package com.golf.erp.billing;

import com.golf.erp.billing.BillingDtos.ServiceFeeCreateRequest;
import com.golf.erp.billing.BillingDtos.ServiceFeeResponse;
import com.golf.erp.billing.BillingDtos.ServiceFeeUpdateRequest;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 부대 요금표(캐디피·카트비·대여료 등) 관리 */
@Service
@Transactional
public class ServiceFeeService {
	private static final Set<Short> HOLE_BASIS = Set.of((short) 9, (short) 18, (short) 27, (short) 36);

	private final ServiceFeeRepository repo;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public ServiceFeeService(ServiceFeeRepository repo, RefChecker refs, AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** @param date 이 날짜에 적용 기간이 걸친 요금만 */
	@Transactional(readOnly = true)
	public PageResult<ServiceFeeResponse> list(Long golfCourseId, FeeType feeType, LocalDate date, Boolean active,
			int page, int size) {
		Specification<ServiceFee> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("feeType", feeType), Specs.eq("active", active), Specs.lte("validFrom", date),
				Specs.gte("validTo", date));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size,
				Sort.by("golfCourseId", "feeType", "name").and(Sort.by(Sort.Direction.DESC, "validFrom")))),
				ServiceFeeResponse::of);
	}

	@Transactional(readOnly = true)
	public ServiceFeeResponse get(long id) {
		return ServiceFeeResponse.of(find(id));
	}

	public ServiceFeeResponse create(ServiceFeeCreateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		LocalDateTime now = now();
		ServiceFee f = new ServiceFee(r.golfCourseId(), now);
		apply(f, r.feeType(), r.name(), r.chargeUnit() == null ? ChargeUnit.TEAM : r.chargeUnit(), r.holeBasis(),
				r.amount(), r.validFrom() == null ? LocalDate.now(clock) : r.validFrom(),
				r.validTo() == null ? ServiceFee.OPEN_END : r.validTo(), r.active() == null || r.active(), now);
		return ServiceFeeResponse.of(repo.save(f));
	}

	public ServiceFeeResponse update(long id, ServiceFeeUpdateRequest r) {
		ServiceFee f = find(id);
		apply(f, r.feeType(), r.name(), r.chargeUnit(), r.holeBasis(), r.amount(), r.validFrom(), r.validTo(),
				r.active(), now());
		return ServiceFeeResponse.of(f);
	}

	/** 이미 부과된 요금이 참조하지 않으므로(값을 복사해 둔다) 물리 삭제 대신 사용 중지로 내린다. */
	public void delete(long id) {
		ServiceFee f = find(id);
		f.deactivate(now());
		audit.log("DELETE", "service_fee", id, Map.of("name", f.getName(), "feeType", f.getFeeType()));
	}

	private void apply(ServiceFee f, FeeType feeType, String name, ChargeUnit unit, Short holeBasis,
			BigDecimal amount, LocalDate validFrom, LocalDate validTo, boolean active, LocalDateTime now) {
		if (holeBasis != null && !HOLE_BASIS.contains(holeBasis))
			throw new IllegalArgumentException("홀 기준은 9·18·27·36 중 하나이거나 비워 두어야 합니다.");
		String n = name.trim();
		if (active && repo.findByGolfCourseIdAndFeeTypeAndNameAndActiveTrue(f.getGolfCourseId(), feeType, n).stream()
				.anyMatch(o -> !o.getId().equals(f.getId()) && Objects.equals(o.getHoleBasis(), holeBasis)
						&& !o.getValidFrom().isAfter(validTo) && !o.getValidTo().isBefore(validFrom)))
			throw new ConflictException("같은 이름·홀 기준의 " + feeType + " 요금이 적용 기간이 겹치게 이미 있습니다: " + n);
		f.update(feeType, n, unit, holeBasis, amount.setScale(2, RoundingMode.HALF_UP), validFrom, validTo, active, now);
	}

	private ServiceFee find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("부대 요금", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
