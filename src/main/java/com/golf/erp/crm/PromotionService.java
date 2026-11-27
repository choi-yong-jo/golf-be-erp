package com.golf.erp.crm;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.crm.CrmDtos.PromotionQuote;
import com.golf.erp.crm.CrmDtos.PromotionRequest;
import com.golf.erp.crm.CrmDtos.PromotionResponse;
import com.golf.erp.crm.CrmDtos.RedemptionRequest;
import com.golf.erp.crm.CrmDtos.RedemptionResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * 프로모션(할인 코드). 코드는 대문자로 저장하고 대소문자 구분 없이 찾는다.
 * 할인액: AMOUNT = min(할인값, 기준 금액), PERCENT = 기준 금액 × 할인율 / 100 원 단위 절사.
 */
@Service
@Transactional
public class PromotionService {
	private final PromotionRepository repo;
	private final PromotionRedemptionRepository redemptions;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final Clock clock;

	public PromotionService(PromotionRepository repo, PromotionRedemptionRepository redemptions, RefChecker refs,
			AuditLogger audit, Clock clock) {
		this.repo = repo;
		this.redemptions = redemptions;
		this.refs = refs;
		this.audit = audit;
		this.clock = clock;
	}

	/** keyword: 코드·이름, current=true: 지금 사용 가능(활성 + 기간 안), false: 사용 불가 */
	@Transactional(readOnly = true)
	public PageResult<PromotionResponse> list(Boolean active, Boolean current, String keyword, int page, int size) {
		LocalDateTime now = now();
		Specification<Promotion> spec = Specification.allOf(Specs.eq("active", active),
				Specs.like(keyword, "code", "name"));
		if (current != null) {
			Specification<Promotion> usable = Specification.allOf(Specs.eq("active", true),
					Specs.lte("startsAt", now), (root, q, cb) -> cb.greaterThan(root.<LocalDateTime>get("endsAt"), now));
			spec = spec.and(current ? usable : Specification.not(usable));
		}
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, Sort.by(Sort.Order.desc("startsAt"),
				Sort.Order.desc("id")))), p -> PromotionResponse.of(p, now));
	}

	@Transactional(readOnly = true)
	public PromotionResponse get(long id) {
		return PromotionResponse.of(find(id), now());
	}

	public PromotionResponse create(PromotionRequest r) {
		String code = normalizeCode(r.code());
		if (repo.existsByCode(code))
			throw new ConflictException("이미 있는 프로모션 코드입니다: " + code);
		Promotion p = new Promotion(code);
		p.update(code, r.name().trim(), r.discountType(), r.discountValue(), r.startsAt(), r.endsAt());
		return PromotionResponse.of(repo.save(p), now());
	}

	/** 이미 사용된 할인액(redemption)은 스냅샷이라 바뀌지 않는다. */
	public PromotionResponse update(long id, PromotionRequest r) {
		Promotion p = find(id);
		String code = normalizeCode(r.code());
		if (repo.existsByCodeAndIdNot(code, id))
			throw new ConflictException("이미 있는 프로모션 코드입니다: " + code);
		p.update(code, r.name().trim(), r.discountType(), r.discountValue(), r.startsAt(), r.endsAt());
		audit.log("UPDATE", "promotion", id, Map.of("code", code, "discountType", r.discountType(), "discountValue",
				r.discountValue()));
		return PromotionResponse.of(p, now());
	}

	public PromotionResponse changeActive(long id, boolean active) {
		Promotion p = find(id);
		if (p.isActive() != active) {
			p.changeActive(active);
			audit.log(active ? "ACTIVATE" : "DEACTIVATE", "promotion", id, Map.of("code", p.getCode()));
		}
		return PromotionResponse.of(p, now());
	}

	/** 코드로 할인액 미리 계산. 코드가 없거나 쓸 수 없으면 valid=false 와 사유를 돌려준다(오류 아님). */
	@Transactional(readOnly = true)
	public PromotionQuote validate(String code, BigDecimal amount) {
		if (code == null || code.isBlank())
			throw new IllegalArgumentException("프로모션 코드를 입력하세요.");
		if (amount == null || amount.signum() < 0)
			throw new IllegalArgumentException("기준 금액은 0 이상이어야 합니다.");
		BigDecimal base = amount.setScale(2, RoundingMode.HALF_UP);
		Promotion p = repo.findByCode(normalizeCode(code)).orElse(null);
		if (p == null)
			return new PromotionQuote(false, "존재하지 않는 프로모션 코드입니다.", null, normalizeCode(code), null, null, null,
					base, BigDecimal.ZERO.setScale(2), base);
		String reason = p.unavailableReason(now());
		BigDecimal discount = reason == null ? p.discountFor(base) : BigDecimal.ZERO.setScale(2);
		return new PromotionQuote(reason == null, reason == null ? "사용 가능한 프로모션입니다." : reason, p.getId(),
				p.getCode(), p.getName(), p.getDiscountType(), p.getDiscountValue(), base, discount,
				base.subtract(discount));
	}

	/**
	 * 프로모션 사용 등록. 활성·기간 안이어야 하고, 같은 예약에 같은 프로모션은 한 번만 쓸 수 있다.
	 * 할인액은 baseAmount 로 서버가 계산해 저장한다.
	 */
	public RedemptionResponse redeem(long promotionId, RedemptionRequest r) {
		Promotion p = repo.findByIdForUpdate(promotionId)
				.orElseThrow(() -> NotFoundException.of("프로모션", promotionId));
		LocalDateTime now = now();
		String reason = p.unavailableReason(now);
		if (reason != null)
			throw new ConflictException(reason);
		refs.require(Ref.MEMBER, r.memberId());
		refs.requireIfPresent(Ref.RESERVATION, r.reservationId());
		if (r.reservationId() != null && redemptions.existsByPromotionIdAndReservationId(promotionId, r.reservationId()))
			throw new ConflictException("이 예약에는 이미 같은 프로모션이 적용되었습니다.");
		BigDecimal discount = p.discountFor(r.baseAmount().setScale(2, RoundingMode.HALF_UP));
		PromotionRedemption saved = redemptions
				.save(new PromotionRedemption(promotionId, r.memberId(), r.reservationId(), now, discount));
		return RedemptionResponse.of(saved, p);
	}

	@Transactional(readOnly = true)
	public PageResult<RedemptionResponse> redemptions(long promotionId, Long memberId, int page, int size) {
		Promotion p = find(promotionId);
		Specification<PromotionRedemption> spec = Specification.allOf(Specs.eq("promotionId", promotionId),
				Specs.eq("memberId", memberId));
		return PageResult.of(redemptions.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("redeemedAt"), Sort.Order.desc("id")))),
				x -> RedemptionResponse.of(x, p));
	}

	private Promotion find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("프로모션", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String normalizeCode(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}
}
