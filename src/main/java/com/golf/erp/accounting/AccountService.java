package com.golf.erp.accounting;

import com.golf.erp.accounting.AccountingDtos.AccountNode;
import com.golf.erp.accounting.AccountingDtos.AccountRequest;
import com.golf.erp.accounting.AccountingDtos.AccountResponse;
import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.Specs;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 계정과목표. 상위 계정은 같은 유형이어야 하고 순환이 생기면 안 된다.
 * 전표·운영비에 한 번이라도 쓰인 계정은 유형을 바꿀 수 없고 삭제 대신 비활성화한다.
 */
@Service
@Transactional
public class AccountService {
	private final AccountRepository repo;
	private final JournalLineRepository journalLines;
	private final OperatingExpenseRepository expenses;
	private final AuditLogger audit;

	public AccountService(AccountRepository repo, JournalLineRepository journalLines,
			OperatingExpenseRepository expenses, AuditLogger audit) {
		this.repo = repo;
		this.journalLines = journalLines;
		this.expenses = expenses;
		this.audit = audit;
	}

	/** 계정과목은 건수가 적은 마스터라 페이지 없이 코드순 전체를 돌려준다. keyword: 코드·이름 */
	@Transactional(readOnly = true)
	public List<AccountResponse> list(AccountType type, Boolean active, String keyword) {
		Specification<Account> spec = Specification.allOf(Specs.eq("type", type), Specs.eq("active", active),
				Specs.like(keyword, "code", "name"));
		return repo.findAll(spec, Sort.by("code")).stream().map(AccountResponse::of).toList();
	}

	/** 계층 구조. activeOnly=true 면 비활성 계정(과 그 하위)은 뺀다. */
	@Transactional(readOnly = true)
	public List<AccountNode> tree(AccountType type, boolean activeOnly) {
		List<Account> all = repo.findAll(Sort.by("code"));
		Map<Long, List<Account>> children = new HashMap<>();
		Set<Long> ids = new HashSet<>();
		for (Account a : all)
			ids.add(a.getId());
		List<Account> roots = new ArrayList<>();
		for (Account a : all) {
			if (a.getParentAccountId() == null || !ids.contains(a.getParentAccountId()))
				roots.add(a);
			else
				children.computeIfAbsent(a.getParentAccountId(), k -> new ArrayList<>()).add(a);
		}
		return roots.stream().filter(a -> type == null || a.getType() == type)
				.filter(a -> !activeOnly || a.isActive()).map(a -> node(a, children, activeOnly)).toList();
	}

	private AccountNode node(Account a, Map<Long, List<Account>> children, boolean activeOnly) {
		List<AccountNode> kids = children.getOrDefault(a.getId(), List.of()).stream()
				.filter(c -> !activeOnly || c.isActive()).map(c -> node(c, children, activeOnly)).toList();
		return new AccountNode(a.getId(), a.getCode(), a.getName(), a.getType(), a.getParentAccountId(), a.isActive(),
				kids);
	}

	@Transactional(readOnly = true)
	public AccountResponse get(long id) {
		return AccountResponse.of(find(id));
	}

	public AccountResponse create(AccountRequest r) {
		String code = r.code().trim();
		if (repo.existsByCode(code))
			throw new ConflictException("이미 있는 계정 코드입니다: " + code);
		if (r.parentAccountId() != null) {
			Account parent = find(r.parentAccountId());
			if (!parent.isActive())
				throw new ConflictException("비활성 계정 아래에는 계정을 만들 수 없습니다: " + parent.getCode());
			requireSameType(parent, r.type());
		}
		Account a = new Account(r.type());
		a.update(code, r.name().trim(), r.type(), r.parentAccountId());
		return AccountResponse.of(repo.save(a));
	}

	public AccountResponse update(long id, AccountRequest r) {
		Account a = find(id);
		String code = r.code().trim();
		if (repo.existsByCodeAndIdNot(code, id))
			throw new ConflictException("이미 있는 계정 코드입니다: " + code);
		if (r.type() != a.getType()) {
			if (isUsed(id))
				throw new ConflictException("전표나 운영비에 사용된 계정은 유형을 바꿀 수 없습니다.");
			if (repo.existsByParentAccountId(id))
				throw new ConflictException("하위 계정이 있는 계정은 유형을 바꿀 수 없습니다.");
		}
		Long parentId = r.parentAccountId();
		if (parentId != null) {
			if (parentId == id)
				throw new IllegalArgumentException("자기 자신을 상위 계정으로 지정할 수 없습니다.");
			Account parent = find(parentId);
			if (a.isActive() && !parent.isActive())
				throw new ConflictException("비활성 계정을 활성 계정의 상위 계정으로 지정할 수 없습니다: " + parent.getCode());
			requireSameType(parent, r.type());
			requireNoCycle(id, parent);
		}
		a.update(code, r.name().trim(), r.type(), parentId);
		return AccountResponse.of(a);
	}

	/** 활성 하위 계정이 있으면 비활성화할 수 없다. */
	public AccountResponse deactivate(long id) {
		Account a = find(id);
		if (!a.isActive())
			return AccountResponse.of(a);
		if (repo.existsByParentAccountIdAndActiveTrue(id))
			throw new ConflictException("활성 하위 계정이 있습니다. 하위 계정을 먼저 비활성화하세요.");
		a.deactivate();
		audit.log("DEACTIVATE", "account", id, Map.of("code", a.getCode()));
		return AccountResponse.of(a);
	}

	/** 상위 계정이 비활성이면 활성화할 수 없다. */
	public AccountResponse activate(long id) {
		Account a = find(id);
		if (a.isActive())
			return AccountResponse.of(a);
		if (a.getParentAccountId() != null && !find(a.getParentAccountId()).isActive())
			throw new ConflictException("상위 계정이 비활성 상태입니다. 상위 계정을 먼저 활성화하세요.");
		a.activate();
		audit.log("ACTIVATE", "account", id, Map.of("code", a.getCode()));
		return AccountResponse.of(a);
	}

	/** 사용 이력·하위 계정이 없는 계정만 삭제한다(그 외는 비활성화). */
	public void delete(long id) {
		Account a = find(id);
		if (repo.existsByParentAccountId(id))
			throw new ConflictException("하위 계정이 있는 계정은 삭제할 수 없습니다.");
		if (isUsed(id))
			throw new ConflictException("전표나 운영비에 사용된 계정은 삭제할 수 없습니다. 비활성화하세요.");
		Map<String, Object> details = new HashMap<>();
		details.put("code", a.getCode());
		details.put("name", a.getName());
		repo.delete(a);
		audit.log("DELETE", "account", id, details);
	}

	private boolean isUsed(long id) {
		return journalLines.existsByAccountId(id) || expenses.existsByAccountId(id);
	}

	private static void requireSameType(Account parent, AccountType type) {
		if (parent.getType() != type)
			throw new IllegalArgumentException(
					"상위 계정(" + parent.getCode() + ")과 같은 유형이어야 합니다. 상위 계정 유형: " + parent.getType());
	}

	/** 새 상위 계정에서 위로 올라가며 자기 자신이 나오면 순환이다. */
	private void requireNoCycle(long selfId, Account parent) {
		Set<Long> seen = new HashSet<>();
		Account cur = parent;
		while (cur != null) {
			if (cur.getId() == selfId)
				throw new IllegalArgumentException("하위 계정을 상위 계정으로 지정할 수 없습니다(순환 구조).");
			if (!seen.add(cur.getId()) || cur.getParentAccountId() == null)
				return;
			cur = repo.findById(cur.getParentAccountId()).orElse(null);
		}
	}

	private Account find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("계정과목", id));
	}
}
