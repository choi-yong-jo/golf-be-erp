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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LodgingRoomRepository extends JpaRepository<LodgingRoom, Long>, JpaSpecificationExecutor<LodgingRoom> {

	/** 응답에 객실 타입 정보를 함께 쓰므로 같이 읽는다(N+1 방지). */
	@Override
	@EntityGraph(attributePaths = "roomType")
	Page<LodgingRoom> findAll(Specification<LodgingRoom> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = "roomType")
	List<LodgingRoom> findAll(Specification<LodgingRoom> spec, Sort sort);

	boolean existsByRoomType_IdAndRoomCode(Long roomTypeId, String roomCode);

	boolean existsByRoomType_IdAndRoomCodeAndIdNot(Long roomTypeId, String roomCode, Long id);

	/**
	 * 예약 등록·입실·퇴실·상태 변경 전 객실 행 잠금(SELECT ... FOR UPDATE). 같은 객실의 겹침 검사를 직렬화한다.
	 * 객실 타입은 잠그지 않고, 잠금 뒤 지연 로딩으로 최신 값을 읽는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from LodgingRoom r where r.id = :id")
	Optional<LodgingRoom> findByIdForUpdate(@Param("id") long id);

	/** 객실 타입 비활성화 시 그 타입의 모든 객실을 id 순서로 잠근다(예약 등록과 직렬화, 교착 방지). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from LodgingRoom r where r.roomType.id = :roomTypeId order by r.id")
	List<LodgingRoom> lockByRoomTypeId(@Param("roomTypeId") long roomTypeId);

	/** 객실 타입 하나의 상태별 객실 수: [status, count] */
	@Query("select r.status, count(r) from LodgingRoom r where r.roomType.id = :roomTypeId group by r.status")
	List<Object[]> countByStatus(@Param("roomTypeId") long roomTypeId);

	/** 객실 타입별 객실 수: [roomTypeId, count] */
	@Query("select r.roomType.id, count(r) from LodgingRoom r where r.roomType.id in :roomTypeIds group by r.roomType.id")
	List<Object[]> countByRoomTypeIds(@Param("roomTypeIds") Collection<Long> roomTypeIds);
}
