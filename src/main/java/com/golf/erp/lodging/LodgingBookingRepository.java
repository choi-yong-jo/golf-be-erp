package com.golf.erp.lodging;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

public interface LodgingBookingRepository
		extends JpaRepository<LodgingBooking, Long>, JpaSpecificationExecutor<LodgingBooking> {

	/** 응답에 객실·객실 타입 정보를 함께 쓰므로 같이 읽는다(N+1 방지). */
	@Override
	@EntityGraph(attributePaths = { "room", "room.roomType" })
	Page<LodgingBooking> findAll(Specification<LodgingBooking> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "room", "room.roomType" })
	List<LodgingBooking> findAll(Specification<LodgingBooking> spec, Sort sort);

	/** 상태 변경 전 예약 행 잠금. 객실도 잠가야 하면 객실을 먼저 잠근다(잠금 순서: 객실 → 예약). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from LodgingBooking b where b.id = :id")
	Optional<LodgingBooking> findByIdForUpdate(@Param("id") long id);

	@Query("select b.room.id from LodgingBooking b where b.id = :id")
	Optional<Long> findRoomIdById(@Param("id") long id);

	/**
	 * 같은 객실에서 [checkIn, checkOut) 과 겹치는 예약(기존.in &lt; 새.out AND 기존.out &gt; 새.in).
	 * excludeId 는 수정 중인 자기 자신(새 예약이면 0).
	 */
	@Query("select b from LodgingBooking b where b.room.id = :roomId and b.status in :statuses"
			+ " and b.scheduledCheckInAt < :checkOut and b.scheduledCheckOutAt > :checkIn and b.id <> :excludeId"
			+ " order by b.scheduledCheckInAt")
	List<LodgingBooking> findOverlapping(@Param("roomId") long roomId,
			@Param("statuses") Collection<LodgingBookingStatus> statuses, @Param("checkIn") LocalDateTime checkIn,
			@Param("checkOut") LocalDateTime checkOut, @Param("excludeId") long excludeId);

	/** 가용 조회용: 여러 객실의 점유 예약 중 [from, to) 와 겹치는 것 */
	@Query("select b from LodgingBooking b where b.room.id in :roomIds and b.status in :statuses"
			+ " and b.scheduledCheckInAt < :to and b.scheduledCheckOutAt > :from")
	List<LodgingBooking> findOverlappingRooms(@Param("roomIds") Collection<Long> roomIds,
			@Param("statuses") Collection<LodgingBookingStatus> statuses, @Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);

	/** 객실에 지금 입실 중인 예약 번호(정상이면 0~1건) */
	@Query("select b.bookingNo from LodgingBooking b where b.room.id = :roomId and b.status = :status")
	List<String> findBookingNosByRoomAndStatus(@Param("roomId") long roomId,
			@Param("status") LodgingBookingStatus status);

	/** 객실의 입실 전(PENDING·CONFIRMED) 예약 중 퇴실 예정 시각이 now 이후인 것의 수 */
	@Query("select count(b) from LodgingBooking b where b.room.id = :roomId and b.status in :upcoming"
			+ " and b.scheduledCheckOutAt > :now")
	long countUpcomingByRoom(@Param("roomId") long roomId,
			@Param("upcoming") Collection<LodgingBookingStatus> upcoming, @Param("now") LocalDateTime now);

	/** 아직 끝나지 않은 예약: 입실 중(CHECKED_IN, 기간 무관)이거나, 입실 전이면서 퇴실 예정 시각이 now 이후 */
	@Query("select count(b) from LodgingBooking b where b.room.id = :roomId and (b.status = :checkedIn"
			+ " or (b.status in :upcoming and b.scheduledCheckOutAt > :now))")
	long countUnfinishedByRoom(@Param("roomId") long roomId, @Param("checkedIn") LodgingBookingStatus checkedIn,
			@Param("upcoming") Collection<LodgingBookingStatus> upcoming, @Param("now") LocalDateTime now);

	@Query("select count(b) from LodgingBooking b where b.room.roomType.id = :roomTypeId and (b.status = :checkedIn"
			+ " or (b.status in :upcoming and b.scheduledCheckOutAt > :now))")
	long countUnfinishedByRoomType(@Param("roomTypeId") long roomTypeId,
			@Param("checkedIn") LodgingBookingStatus checkedIn,
			@Param("upcoming") Collection<LodgingBookingStatus> upcoming, @Param("now") LocalDateTime now);
}
