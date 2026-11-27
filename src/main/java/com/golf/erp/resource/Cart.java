package com.golf.erp.resource;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** golf.cart. cart_code 는 cart_no 와 같은 뜻의 중복 컬럼이라 같은 값으로 저장한다. */
@Entity
@Table(name = "cart")
public class Cart {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "cart_id")
	private Long id;
	@Column(name = "golf_course_id", nullable = false)
	private Long golfCourseId;
	@Column(name = "cart_no", nullable = false, length = 20)
	private String cartNo;
	@Column(name = "cart_code", length = 30)
	private String cartCode;
	@Column(name = "seat_count", nullable = false)
	private Short seatCount;
	@Enumerated(EnumType.STRING)
	@Column(name = "cart_status", nullable = false, length = 20)
	private CartStatus status = CartStatus.ACTIVE;
	@Column(name = "purchase_date")
	private LocalDate purchaseDate;
	@Column(name = "last_maintenance_at")
	private LocalDateTime lastMaintenanceAt;
	@Column(length = 500)
	private String memo;
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Cart() {
	}

	public Cart(Long golfCourseId, String cartNo, LocalDateTime now) {
		this.golfCourseId = golfCourseId;
		this.cartNo = cartNo;
		this.cartCode = cartNo;
		this.createdAt = now;
	}

	public void update(short seatCount, LocalDate purchaseDate, String memo, LocalDateTime now) {
		this.seatCount = seatCount;
		this.purchaseDate = purchaseDate;
		this.memo = memo;
		this.updatedAt = now;
	}

	/** MAINTENANCE 에서 ACTIVE 로 돌아오면 마지막 정비 시각을 남긴다. */
	public void changeStatus(CartStatus status, LocalDateTime now) {
		if (this.status == CartStatus.MAINTENANCE && status == CartStatus.ACTIVE)
			this.lastMaintenanceAt = now;
		this.status = status;
		this.updatedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getGolfCourseId() {
		return golfCourseId;
	}

	public String getCartNo() {
		return cartNo;
	}

	public Short getSeatCount() {
		return seatCount;
	}

	public CartStatus getStatus() {
		return status;
	}

	public LocalDate getPurchaseDate() {
		return purchaseDate;
	}

	public LocalDateTime getLastMaintenanceAt() {
		return lastMaintenanceAt;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
