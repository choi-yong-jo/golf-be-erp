package com.golf.erp.resource;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.resource.ResourceDtos.CartCreateRequest;
import com.golf.erp.resource.ResourceDtos.CartResponse;
import com.golf.erp.resource.ResourceDtos.CartUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional
public class CartService {
	private static final short DEFAULT_SEATS = 5;

	private final CartRepository repo;
	private final AssignmentLookup assignments;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public CartService(CartRepository repo, AssignmentLookup assignments, RefChecker refs, AuditLogger audit,
			Clock clock) {
		this.repo = repo;
		this.assignments = assignments;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** @param available true 면 ACTIVE 이면서 지금 배정되지 않은 카트만, false 면 배정 중인 카트만 */
	@Transactional(readOnly = true)
	public PageResult<CartResponse> list(Long golfCourseId, CartStatus status, String keyword, Boolean available,
			int page, int size) {
		Map<Long, Long> busy = assignments.activeCarts();
		Specification<Cart> spec = Specification.allOf(Specs.eq("golfCourseId", golfCourseId),
				Specs.eq("status", status), Specs.like(keyword, "cartNo"));
		if (Boolean.TRUE.equals(available)) {
			spec = spec.and(Specs.eq("status", CartStatus.ACTIVE));
			if (!busy.isEmpty())
				spec = spec.and(Specification.not(Specs.in("id", busy.keySet())));
		} else if (Boolean.FALSE.equals(available)) {
			spec = spec.and(busy.isEmpty() ? (r, q, cb) -> cb.disjunction() : Specs.in("id", busy.keySet()));
		}
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by("cartNo"))),
				c -> CartResponse.of(c, busy.get(c.getId())));
	}

	@Transactional(readOnly = true)
	public CartResponse get(long id) {
		Cart c = find(id);
		return CartResponse.of(c, assignments.activeCarts().get(id));
	}

	public CartResponse create(CartCreateRequest r) {
		refs.require(Ref.GOLF_COURSE, r.golfCourseId());
		String no = r.cartNo().trim();
		if (repo.existsByGolfCourseIdAndCartNo(r.golfCourseId(), no))
			throw new ConflictException("이미 있는 카트 번호입니다: " + no);
		LocalDateTime now = now();
		Cart c = new Cart(r.golfCourseId(), no, now);
		c.update(r.seatCount() == null ? DEFAULT_SEATS : r.seatCount(), r.purchaseDate(), blankToNull(r.memo()), now);
		return CartResponse.of(repo.save(c), null);
	}

	public CartResponse update(long id, CartUpdateRequest r) {
		Cart c = find(id);
		c.update(r.seatCount(), r.purchaseDate(), blankToNull(r.memo()), now());
		return CartResponse.of(c, assignments.activeCarts().get(id));
	}

	/** 라운드에 배정 중인 카트는 정비·폐기로 바꿀 수 없다(먼저 배정 해제). */
	public CartResponse changeStatus(long id, CartStatus status) {
		Cart c = find(id);
		Long roundId = assignments.activeCarts().get(id);
		if (status != CartStatus.ACTIVE && roundId != null)
			throw new ConflictException("라운드(" + roundId + ")에 배정 중인 카트입니다. 배정을 먼저 해제하세요.");
		CartStatus before = c.getStatus();
		c.changeStatus(status, now());
		audit.log("STATUS_CHANGE", "cart", id, Map.of("before", before, "after", status));
		return CartResponse.of(c, roundId);
	}

	private Cart find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("카트", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
