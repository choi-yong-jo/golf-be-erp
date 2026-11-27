package com.golf.erp.store;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.DocumentNo;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Ref;
import com.golf.erp.common.RefChecker;
import com.golf.erp.common.Specs;
import com.golf.erp.store.StoreOrderDtos.*;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 매장 주문 접수·진행·결제·환불과 매출 요약.
 * <ul>
 * <li>주문 변경(항목·상태·결제)은 주문 행을 PESSIMISTIC_WRITE 로 잠그고 한다.</li>
 * <li>주문 금액: 단가는 주문 시점 메뉴 판매가를 복사, 취소된 항목은 소계에서 빠지고 변경마다 소계·합계를 다시 계산한다.</li>
 * <li>결제된 주문의 취소는 같은 처리에서 환불(REFUNDED)로 바꾼다(감사 로그 CANCEL + REFUND).</li>
 * </ul>
 */
@Service
@Transactional
public class StoreOrderService {
	/** 매출 요약 조회 기간 상한(일) */
	private static final int MAX_SUMMARY_DAYS = 366;
	private static final String ORDER_NO_PREFIX = "SO";

	private final StoreOrderRepository repo;
	private final StoreMenuItemRepository menuItems;
	private final TenantStoreRepository stores;
	private final RefChecker refs;
	private final AuditLogger audit;
	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock;

	public StoreOrderService(StoreOrderRepository repo, StoreMenuItemRepository menuItems,
			TenantStoreRepository stores, RefChecker refs, AuditLogger audit, JdbcTemplate jdbc, Clock clock) {
		this.repo = repo;
		this.menuItems = menuItems;
		this.stores = stores;
		this.refs = refs;
		this.audit = audit;
		this.jdbc = new NamedParameterJdbcTemplate(jdbc);
		this.clock = clock;
	}

	// ---- 조회 ----

	/**
	 * 접수 시각 최근 순.
	 *
	 * @param golfCourseId 매장이 속한 골프장
	 * @param from         접수일(ordered_at) 시작, to 는 그날 포함
	 * @param keyword      주문번호·고객명·고객 연락처
	 */
	@Transactional(readOnly = true)
	public PageResult<StoreOrderRow> list(Long storeId, Long golfCourseId, StoreOrderStatus status,
			StorePaymentStatus paymentStatus, StoreOrderType orderType, LocalDate from, LocalDate to, Long memberId,
			Long roundId, String keyword, int page, int size) {
		if (from != null && to != null && to.isBefore(from))
			throw new IllegalArgumentException("조회 종료일은 시작일보다 빠를 수 없습니다.");
		Specification<StoreOrder> spec = Specification.allOf(Specs.eq("storeId", storeId), inGolfCourse(golfCourseId),
				Specs.eq("status", status), Specs.eq("paymentStatus", paymentStatus), Specs.eq("orderType", orderType),
				Specs.gte("orderedAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("orderedAt", to == null ? null : to.plusDays(1).atStartOfDay()), Specs.eq("memberId", memberId),
				Specs.eq("roundId", roundId), Specs.like(keyword, "orderNo", "customerName", "customerPhone"));
		Page<StoreOrder> result = repo.findAll(spec,
				Pages.of(page, size, Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id"))));
		Map<Long, String> names = stores.findAllById(result.getContent().stream().map(StoreOrder::getStoreId)
				.collect(Collectors.toSet())).stream()
				.collect(Collectors.toMap(TenantStore::getId, TenantStore::getStoreName));
		return PageResult.of(result, o -> StoreOrderRow.of(o, names.get(o.getStoreId())));
	}

	@Transactional(readOnly = true)
	public StoreOrderResponse get(long id) {
		return toResponse(repo.findById(id).orElseThrow(() -> notFound(id)));
	}

	// ---- 접수·항목 ----

	/**
	 * 주문 접수. 매장은 영업 중(ACTIVE), 메뉴는 같은 매장의 판매 가능 메뉴. 메뉴명·판매가를 주문 시점 값으로 복사한다.
	 * 주문번호는 임시 번호로 저장한 뒤 SO-yyyyMMdd-id 로 바꾼다.
	 */
	public StoreOrderResponse create(StoreOrderCreateRequest r) {
		TenantStore store = requireActiveStore(r.storeId());
		refs.requireIfPresent(Ref.MEMBER, r.memberId());
		refs.requireIfPresent(Ref.ROUND, r.roundId());
		Map<Long, StoreMenuItem> menus = orderableMenus(store.getId(),
				r.items().stream().map(OrderLineRequest::menuItemId).collect(Collectors.toSet()));
		LocalDateTime now = now();
		StoreOrder o = new StoreOrder(store.getId(), DocumentNo.temporary(), r.orderType(), r.memberId(),
				r.roundId(), blankToNull(r.customerName()), blankToNull(r.customerPhone()),
				blankToNull(r.specialRequest()), now);
		for (OrderLineRequest line : r.items()) {
			StoreMenuItem m = menus.get(line.menuItemId());
			o.addItem(m.getId(), m.getMenuName(), line.quantity(), m.getSalePrice(), line.discountAmount(),
					blankToNull(line.itemRequest()));
		}
		o.applyDiscount(r.discountAmount());
		StoreOrder saved = repo.saveAndFlush(o);
		saved.assignOrderNo(DocumentNo.of(ORDER_NO_PREFIX, saved.getOrderedAt().toLocalDate(), saved.getId()));
		return toResponse(saved, store);
	}

	/** 항목 추가(PLACED/ACCEPTED/PREPARING, 미결제). 매장이 영업 중이어야 한다. */
	public StoreOrderResponse addItem(long id, OrderLineRequest r) {
		StoreOrder o = lock(id);
		TenantStore store = requireActiveStore(o.getStoreId());
		StoreMenuItem m = orderableMenus(o.getStoreId(), Set.of(r.menuItemId())).get(r.menuItemId());
		o.addItem(m.getId(), m.getMenuName(), r.quantity(), m.getSalePrice(), r.discountAmount(),
				blankToNull(r.itemRequest()));
		repo.flush();
		return toResponse(o, store);
	}

	/** 항목 취소(PLACED/ACCEPTED/PREPARING, 미결제). 소계·합계를 다시 계산하고 감사 로그를 남긴다. */
	public StoreOrderResponse cancelItem(long id, long itemId, CancelItemRequest r) {
		StoreOrder o = lock(id);
		BigDecimal totalBefore = o.getTotalAmount();
		StoreOrderItem item = o.cancelItem(itemId, r == null ? null : r.orderDiscountAmount());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("orderNo", o.getOrderNo());
		details.put("itemId", itemId);
		details.put("menuName", item.getMenuNameSnapshot());
		details.put("lineAmount", item.getLineAmount());
		details.put("totalBefore", totalBefore);
		details.put("totalAfter", o.getTotalAmount());
		String reason = r == null ? null : blankToNull(r.reason());
		if (reason != null)
			details.put("reason", reason);
		audit.log("CANCEL_ITEM", "store_order", id, details);
		return toResponse(o);
	}

	/** 항목 조리 진행(ORDERED → PREPARING → READY → SERVED, 앞으로만) */
	public StoreOrderResponse changeItemStatus(long id, long itemId, StoreOrderItemStatus status) {
		StoreOrder o = lock(id);
		o.changeItemStatus(itemId, status);
		return toResponse(o);
	}

	// ---- 진행·취소 ----

	/** 다음 단계로만(PLACED → ACCEPTED → PREPARING → READY → COMPLETED) */
	public StoreOrderResponse changeStatus(long id, StoreOrderStatus status) {
		StoreOrder o = lock(id);
		o.advance(status, now());
		return toResponse(o);
	}

	/**
	 * 주문 취소(COMPLETED·CANCELLED 제외, 사유 필수). 결제된 주문은 같은 처리에서 환불(REFUNDED)로 바꾼다
	 * — 취소됐는데 결제만 남는 상태를 만들지 않기 위해서다. 감사 로그 CANCEL(환불했으면 REFUND 도).
	 */
	public StoreOrderResponse cancel(long id, String reason) {
		StoreOrder o = lock(id);
		StoreOrderStatus before = o.getStatus();
		String why = reason.trim();
		boolean refunded = o.cancel(why, now());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("orderNo", o.getOrderNo());
		details.put("before", before);
		details.put("totalAmount", o.getTotalAmount());
		details.put("refunded", refunded);
		details.put("reason", why);
		audit.log("CANCEL", "store_order", id, details);
		if (refunded)
			audit.log("REFUND", "store_order", id, refundDetails(o, "주문 취소: " + why));
		return toResponse(o);
	}

	// ---- 결제·환불 ----

	/** 결제(UNPAID → PAID, paid_at 기록). 취소된 주문은 결제할 수 없다. */
	public StoreOrderResponse pay(long id) {
		StoreOrder o = lock(id);
		o.pay(now());
		return toResponse(o);
	}

	/** 환불(PAID → REFUNDED). 주문 진행 상태는 바꾸지 않는다. 감사 로그 REFUND. */
	public StoreOrderResponse refund(long id, String reason) {
		StoreOrder o = lock(id);
		o.refund();
		audit.log("REFUND", "store_order", id, refundDetails(o, blankToNull(reason)));
		return toResponse(o);
	}

	// ---- 매출 요약 ----

	/**
	 * 매장별·날짜별 매출. from/to 생략 시 오늘, 최대 {@value #MAX_SUMMARY_DAYS}일.
	 * 주문 수·취소 수는 접수일(ordered_at), 결제·환불 금액은 결제일(paid_at) 기준(환불 시각 컬럼이 없어 결제일로 집계).
	 * paidTotal 은 현재 PAID 인 주문(순매출), refundedTotal 은 결제 후 환불된 주문 합계.
	 */
	@Transactional(readOnly = true)
	public StoreSalesSummaryResponse summary(Long storeId, Long golfCourseId, LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from != null ? from : (to != null ? to : today);
		LocalDate end = to != null ? to : (from != null ? from : today);
		if (end.isBefore(start))
			throw new IllegalArgumentException("조회 종료일은 시작일보다 빠를 수 없습니다.");
		if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_SUMMARY_DAYS)
			throw new IllegalArgumentException("매출 요약 조회 기간은 최대 " + MAX_SUMMARY_DAYS + "일입니다.");
		if (storeId != null && !stores.existsById(storeId))
			throw NotFoundException.of(Ref.TENANT_STORE.label, storeId);

		MapSqlParameterSource p = new MapSqlParameterSource().addValue("start", start.atStartOfDay())
				.addValue("end", end.plusDays(1).atStartOfDay()).addValue("storeId", storeId)
				.addValue("golfCourseId", golfCourseId);
		String filter = (storeId == null ? "" : " AND o.store_id = :storeId")
				+ (golfCourseId == null ? "" : " AND s.golf_course_id = :golfCourseId");
		Map<Long, TreeMap<LocalDate, Acc>> byStore = new HashMap<>();

		jdbc.query("SELECT o.store_id, CAST(o.ordered_at AS date) AS d,"
				+ " COUNT(*) FILTER (WHERE o.order_status <> 'CANCELLED'),"
				+ " COUNT(*) FILTER (WHERE o.order_status = 'CANCELLED')"
				+ " FROM golf.store_order o JOIN golf.tenant_store s ON s.store_id = o.store_id"
				+ " WHERE o.ordered_at >= :start AND o.ordered_at < :end" + filter + " GROUP BY o.store_id, d", p,
				(RowCallbackHandler) rs -> {
					Acc a = acc(byStore, rs.getLong(1), rs.getObject(2, LocalDate.class));
					a.orderCount += rs.getLong(3);
					a.cancelledCount += rs.getLong(4);
				});
		jdbc.query("SELECT o.store_id, CAST(o.paid_at AS date) AS d,"
				+ " COUNT(*) FILTER (WHERE o.payment_status = 'PAID'),"
				+ " COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'PAID'), 0),"
				+ " COUNT(*) FILTER (WHERE o.payment_status = 'REFUNDED'),"
				+ " COALESCE(SUM(o.total_amount) FILTER (WHERE o.payment_status = 'REFUNDED'), 0)"
				+ " FROM golf.store_order o JOIN golf.tenant_store s ON s.store_id = o.store_id"
				+ " WHERE o.paid_at >= :start AND o.paid_at < :end AND o.payment_status IN ('PAID', 'REFUNDED')"
				+ filter + " GROUP BY o.store_id, d", p, (RowCallbackHandler) rs -> {
					Acc a = acc(byStore, rs.getLong(1), rs.getObject(2, LocalDate.class));
					a.paidCount += rs.getLong(3);
					a.paidTotal = a.paidTotal.add(rs.getBigDecimal(4));
					a.refundedCount += rs.getLong(5);
					a.refundedTotal = a.refundedTotal.add(rs.getBigDecimal(6));
				});

		Set<Long> storeIds = new HashSet<>(byStore.keySet());
		if (storeId != null)
			storeIds.add(storeId);
		List<TenantStore> storeList = new ArrayList<>(stores.findAllById(storeIds));
		storeList.sort(Comparator.comparing(TenantStore::getGolfCourseId).thenComparing(TenantStore::getStoreCode));
		Acc grand = new Acc();
		List<StoreSales> result = new ArrayList<>();
		for (TenantStore s : storeList) {
			Acc total = new Acc();
			List<DailySales> days = new ArrayList<>();
			byStore.getOrDefault(s.getId(), new TreeMap<>()).forEach((date, a) -> {
				total.add(a);
				days.add(new DailySales(date, a.orderCount, a.cancelledCount, a.paidCount, money(a.paidTotal),
						a.refundedCount, money(a.refundedTotal)));
			});
			grand.add(total);
			result.add(new StoreSales(s.getId(), s.getStoreCode(), s.getStoreName(), s.getGolfCourseId(),
					total.orderCount, total.cancelledCount, total.paidCount, money(total.paidTotal),
					total.refundedCount, money(total.refundedTotal), days));
		}
		return new StoreSalesSummaryResponse(start, end, grand.orderCount, grand.cancelledCount, grand.paidCount,
				money(grand.paidTotal), grand.refundedCount, money(grand.refundedTotal), result);
	}

	/** 매출 집계 누적값 */
	private static final class Acc {
		long orderCount;
		long cancelledCount;
		long paidCount;
		BigDecimal paidTotal = BigDecimal.ZERO;
		long refundedCount;
		BigDecimal refundedTotal = BigDecimal.ZERO;

		void add(Acc o) {
			orderCount += o.orderCount;
			cancelledCount += o.cancelledCount;
			paidCount += o.paidCount;
			paidTotal = paidTotal.add(o.paidTotal);
			refundedCount += o.refundedCount;
			refundedTotal = refundedTotal.add(o.refundedTotal);
		}
	}

	private static Acc acc(Map<Long, TreeMap<LocalDate, Acc>> byStore, long storeId, LocalDate date) {
		return byStore.computeIfAbsent(storeId, k -> new TreeMap<>()).computeIfAbsent(date, k -> new Acc());
	}

	private static BigDecimal money(BigDecimal v) {
		return v.setScale(2, RoundingMode.HALF_UP);
	}

	// ---- 내부 ----

	/**
	 * 같은 매장의 판매 가능한 메뉴만 주문할 수 있다. 없는 메뉴·다른 매장 메뉴 → 404, 품절 → 409.
	 * 반환: 메뉴 id → 메뉴
	 */
	private Map<Long, StoreMenuItem> orderableMenus(long storeId, Set<Long> menuItemIds) {
		Map<Long, StoreMenuItem> found = menuItems.findAllById(menuItemIds).stream()
				.filter(m -> m.getStoreId().equals(storeId))
				.collect(Collectors.toMap(StoreMenuItem::getId, m -> m));
		for (Long mid : menuItemIds) {
			StoreMenuItem m = found.get(mid);
			if (m == null)
				throw new NotFoundException("이 매장의 메뉴가 아닙니다: " + mid);
			if (!m.isAvailable())
				throw new ConflictException("품절(판매 중지)된 메뉴입니다: " + m.getMenuName());
		}
		return found;
	}

	private TenantStore requireActiveStore(long storeId) {
		TenantStore s = stores.findById(storeId)
				.orElseThrow(() -> NotFoundException.of(Ref.TENANT_STORE.label, storeId));
		if (s.getStatus() != StoreStatus.ACTIVE)
			throw new ConflictException("영업 중(ACTIVE)인 매장만 주문을 받을 수 있습니다. 현재: " + s.getStatus());
		return s;
	}

	/** 매장이 이 골프장에 속한 주문 */
	private static Specification<StoreOrder> inGolfCourse(Long golfCourseId) {
		if (golfCourseId == null)
			return null;
		return (root, q, cb) -> {
			Subquery<Long> sq = q.subquery(Long.class);
			Root<TenantStore> s = sq.from(TenantStore.class);
			sq.select(s.get("id")).where(cb.equal(s.get("golfCourseId"), golfCourseId));
			return root.get("storeId").in(sq);
		};
	}

	private static Map<String, Object> refundDetails(StoreOrder o, String reason) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("orderNo", o.getOrderNo());
		details.put("amount", o.getTotalAmount());
		details.put("paidAt", String.valueOf(o.getPaidAt()));
		if (reason != null)
			details.put("reason", reason);
		return details;
	}

	private StoreOrder lock(long id) {
		return repo.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
	}

	private StoreOrderResponse toResponse(StoreOrder o) {
		return toResponse(o, stores.findById(o.getStoreId()).orElse(null));
	}

	private static StoreOrderResponse toResponse(StoreOrder o, TenantStore s) {
		return StoreOrderResponse.of(o, s);
	}

	private static NotFoundException notFound(long id) {
		return NotFoundException.of("매장 주문", id);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
