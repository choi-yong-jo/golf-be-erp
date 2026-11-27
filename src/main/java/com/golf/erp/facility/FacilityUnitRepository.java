package com.golf.erp.facility;

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

public interface FacilityUnitRepository extends JpaRepository<FacilityUnit, Long>, JpaSpecificationExecutor<FacilityUnit> {

	/** 목록 응답에 시설·공간 종류 정보를 함께 쓰므로 같이 읽는다(N+1 방지). */
	@Override
	@EntityGraph(attributePaths = { "facility", "roomType" })
	Page<FacilityUnit> findAll(Specification<FacilityUnit> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "facility", "roomType" })
	List<FacilityUnit> findAll(Specification<FacilityUnit> spec, Sort sort);

	boolean existsByFacility_IdAndUnitCode(Long facilityId, String unitCode);

	boolean existsByFacility_IdAndUnitCodeAndIdNot(Long facilityId, String unitCode, Long id);

	@Query("select u from FacilityUnit u join fetch u.facility join fetch u.roomType"
			+ " where u.facility.id = :facilityId order by u.unitCode")
	List<FacilityUnit> findByFacilityId(@Param("facilityId") long facilityId);

	/** 잠그지 않고 시설 id 만 읽는다(공간 수정 시 공간 종류를 먼저 잠그기 위해). */
	@Query("select u.facility.id from FacilityUnit u where u.id = :id")
	Optional<Long> findFacilityIdById(@Param("id") long id);

	/**
	 * 예약 등록·기간 변경·상태 변경 전 공간 행 잠금(SELECT ... FOR UPDATE). 같은 공간의 겹침 검사를 직렬화한다.
	 * 시설·공간 종류는 잠그지 않는다(잠금 뒤 지연 로딩으로 최신 값을 읽는다).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from FacilityUnit u where u.id = :id")
	Optional<FacilityUnit> findByIdForUpdate(@Param("id") long id);

	/** 시설 비활성화 시 그 시설의 모든 공간을 id 순서로 잠근다(예약 등록과 직렬화, 교착 방지). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from FacilityUnit u where u.facility.id = :facilityId order by u.id")
	List<FacilityUnit> lockByFacilityId(@Param("facilityId") long facilityId);

	/** 공간 종류 비활성화 시 그 종류의 모든 공간을 id 순서로 잠근다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from FacilityUnit u where u.roomType.id = :roomTypeId order by u.id")
	List<FacilityUnit> lockByRoomTypeId(@Param("roomTypeId") long roomTypeId);

	/** facility_id → 공간 수 */
	@Query("select u.facility.id, count(u) from FacilityUnit u where u.facility.id in :facilityIds group by u.facility.id")
	List<Object[]> countByFacilityIds(@Param("facilityIds") Collection<Long> facilityIds);

	/** facility_room_type_id → 공간 수 */
	@Query("select u.roomType.id, count(u) from FacilityUnit u where u.roomType.id in :roomTypeIds"
			+ " group by u.roomType.id")
	List<Object[]> countByRoomTypeIds(@Param("roomTypeIds") Collection<Long> roomTypeIds);

	long countByFacility_Id(Long facilityId);

	long countByRoomType_Id(Long roomTypeId);
}
