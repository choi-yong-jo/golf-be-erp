package com.golf.erp.facility;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FacilityBookingRepository
		extends JpaRepository<FacilityBooking, Long>, JpaSpecificationExecutor<FacilityBooking> {

	/** 목록 응답에 공간·시설·공간 종류 정보를 함께 쓰므로 같이 읽는다(N+1 방지). */
	@Override
	@EntityGraph(attributePaths = { "unit", "unit.facility", "unit.roomType" })
	Page<FacilityBooking> findAll(Specification<FacilityBooking> spec, Pageable pageable);

	/** 상태 변경 전 예약 행 잠금. 잠금 순서는 항상 공간 → 예약 이다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from FacilityBooking b where b.id = :id")
	Optional<FacilityBooking> findByIdForUpdate(@Param("id") long id);

	@Query("select b.unit.id from FacilityBooking b where b.id = :id")
	Optional<Long> findUnitIdById(@Param("id") long id);

	/**
	 * 같은 공간에서 [startAt, endAt) 과 겹치는 예약(반열린 구간: 기존.start &lt; 새.end AND 기존.end &gt; 새.start).
	 * excludeId 는 수정 중인 자기 자신(새 예약이면 0).
	 */
	@Query("select b from FacilityBooking b where b.unit.id = :unitId and b.status in :statuses"
			+ " and b.startAt < :endAt and b.endAt > :startAt and b.id <> :excludeId order by b.startAt")
	List<FacilityBooking> findOverlapping(@Param("unitId") long unitId,
			@Param("statuses") Collection<FacilityBookingStatus> statuses, @Param("startAt") LocalDateTime startAt,
			@Param("endAt") LocalDateTime endAt, @Param("excludeId") long excludeId);

	/** 시설의 끝나지 않은 예약: 입실 전(PENDING·CONFIRMED)이면서 종료 시각이 now 이후이거나, 입실 중(CHECKED_IN, 기간 무관) */
	@Query("select count(b) from FacilityBooking b where b.unit.facility.id = :facilityId and (b.status = :checkedIn"
			+ " or (b.status in :upcoming and b.endAt > :now))")
	long countUnfinishedByFacility(@Param("facilityId") long facilityId,
			@Param("checkedIn") FacilityBookingStatus checkedIn,
			@Param("upcoming") Collection<FacilityBookingStatus> upcoming, @Param("now") LocalDateTime now);

	/** 공간 종류(현재 그 종류에 속한 공간)의 끝나지 않은 예약 수 */
	@Query("select count(b) from FacilityBooking b where b.unit.roomType.id = :roomTypeId and (b.status = :checkedIn"
			+ " or (b.status in :upcoming and b.endAt > :now))")
	long countUnfinishedByRoomType(@Param("roomTypeId") long roomTypeId,
			@Param("checkedIn") FacilityBookingStatus checkedIn,
			@Param("upcoming") Collection<FacilityBookingStatus> upcoming, @Param("now") LocalDateTime now);

	/** 공간의 입실 전 예약 중 종료 시각이 now 이후인 것의 수 */
	long countByUnit_IdAndStatusInAndEndAtAfter(Long unitId, Collection<FacilityBookingStatus> statuses,
			LocalDateTime now);

	/** 아직 끝나지 않은 예약 중 가장 많은 인원(없으면 null). 수용 인원을 줄일 때 확인한다. */
	@Query("select max(b.guestCount) from FacilityBooking b where b.unit.id = :unitId and (b.status = :checkedIn"
			+ " or (b.status in :upcoming and b.endAt > :now))")
	Integer maxUnfinishedGuestCount(@Param("unitId") long unitId, @Param("checkedIn") FacilityBookingStatus checkedIn,
			@Param("upcoming") Collection<FacilityBookingStatus> upcoming, @Param("now") LocalDateTime now);

	/** 입실 중인 예약 번호(없으면 빈 목록) */
	@Query("select b.bookingNo from FacilityBooking b where b.unit.id = :unitId and b.status = :status")
	List<String> findBookingNosByUnitAndStatus(@Param("unitId") long unitId,
			@Param("status") FacilityBookingStatus status);

	/** 달력용: 시설의 공간별 예약 중 [from, to) 와 겹치는 것 */
	@Query("select b from FacilityBooking b where b.unit.facility.id = :facilityId and b.status in :statuses"
			+ " and b.startAt < :to and b.endAt > :from order by b.startAt, b.id")
	List<FacilityBooking> findForCalendar(@Param("facilityId") long facilityId,
			@Param("statuses") Collection<FacilityBookingStatus> statuses, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);
}
