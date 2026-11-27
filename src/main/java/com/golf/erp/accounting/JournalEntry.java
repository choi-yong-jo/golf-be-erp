package com.golf.erp.accounting;

import com.golf.erp.common.ConflictException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * golf.journal_entry. 회계 전표(머리). DRAFT 에서만 내용·줄을 바꿀 수 있고, 전기(POSTED) 후에는 무효(VOID)만 가능하다.
 * entry_no 는 id 를 포함하므로 임시 번호로 저장한 뒤 바꾼다.
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "journal_entry_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "entry_no", nullable = false, unique = true, length = 40)
	private String entryNo;
	@Column(name = "business_date", nullable = false)
	private LocalDate businessDate;
	@Column(name = "description", length = 500)
	private String description;
	@Column(name = "source_type", length = 30)
	private String sourceType;
	@Column(name = "source_id")
	private Long sourceId;
	@Enumerated(EnumType.STRING)
	@Column(name = "entry_status", nullable = false, length = 20)
	private JournalStatus status = JournalStatus.DRAFT;
	@Column(name = "created_by")
	private Long createdBy;
	@Column(name = "posted_at")
	private LocalDateTime postedAt;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	private List<JournalLine> lines = new ArrayList<>();

	protected JournalEntry() {
	}

	public JournalEntry(String temporaryNo, Long createdBy, LocalDateTime now) {
		this.entryNo = temporaryNo;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public void assignNo(String entryNo) {
		this.entryNo = entryNo;
	}

	public void update(Long golfCourseId, LocalDate businessDate, String description, String sourceType,
			Long sourceId) {
		requireDraft("수정");
		this.golfCourseId = golfCourseId;
		this.businessDate = businessDate;
		this.description = description;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
	}

	/** 줄 전체 교체 */
	public void replaceLines(List<JournalLine> newLines) {
		requireDraft("수정");
		lines.clear();
		for (JournalLine l : newLines) {
			l.attachTo(this);
			lines.add(l);
		}
	}

	/** 차대 균형 검사 후 전기. 계정 활성 여부는 서비스가 먼저 확인한다. */
	public void post(LocalDateTime now) {
		requireDraft("전기");
		if (lines.size() < 2)
			throw new IllegalArgumentException("전표는 두 줄 이상이어야 전기할 수 있습니다.");
		BigDecimal debit = totalDebit();
		BigDecimal credit = totalCredit();
		if (debit.signum() <= 0 || debit.compareTo(credit) != 0)
			throw new IllegalArgumentException("차변 합계(" + debit.toPlainString() + ")와 대변 합계(" + credit.toPlainString()
					+ ")가 같고 0 보다 커야 전기할 수 있습니다.");
		this.status = JournalStatus.POSTED;
		this.postedAt = now;
	}

	public void voidEntry() {
		if (status != JournalStatus.POSTED)
			throw new ConflictException("전기(POSTED)된 전표만 무효 처리할 수 있습니다. 현재: " + status);
		this.status = JournalStatus.VOID;
	}

	public void requireDraft(String action) {
		if (status != JournalStatus.DRAFT)
			throw new ConflictException("작성 중(DRAFT)인 전표만 " + action + "할 수 있습니다. 현재: " + status);
	}

	public BigDecimal totalDebit() {
		return lines.stream().map(JournalLine::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public BigDecimal totalCredit() {
		return lines.stream().map(JournalLine::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public String getEntryNo() {
		return entryNo;
	}

	public LocalDate getBusinessDate() {
		return businessDate;
	}

	public String getDescription() {
		return description;
	}

	public String getSourceType() {
		return sourceType;
	}

	public Long getSourceId() {
		return sourceId;
	}

	public JournalStatus getStatus() {
		return status;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getPostedAt() {
		return postedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public List<JournalLine> getLines() {
		return Collections.unmodifiableList(lines);
	}
}
