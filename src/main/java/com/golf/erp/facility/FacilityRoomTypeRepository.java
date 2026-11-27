package com.golf.erp.facility;

import jakarta.persistence.LockModeType;
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

public interface FacilityRoomTypeRepository
		extends JpaRepository<FacilityRoomType, Long>, JpaSpecificationExecutor<FacilityRoomType> {

	/** 목록 응답에 시설 정보를 함께 쓰므로 같이 읽는다(N+1 방지). */
	@Override
	@EntityGraph(attributePaths = "facility")
	List<FacilityRoomType> findAll(Specification<FacilityRoomType> spec, Sort sort);

	boolean existsByFacility_IdAndCode(Long facilityId, String code);

	boolean existsByFacility_IdAndCodeAndIdNot(Long facilityId, String code, Long id);

	/**
	 * 공간 종류 행 잠금(SELECT ... FOR UPDATE). 비활성화·수정, 공간 등록·종류 변경 전에 잡는다.
	 * 잠금 순서는 항상 공간 종류 → 공간 → 예약.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from FacilityRoomType t where t.id = :id")
	Optional<FacilityRoomType> findByIdForUpdate(@Param("id") long id);

	/** facility_id → 공간 종류 수 */
	@Query("select t.facility.id, count(t) from FacilityRoomType t where t.facility.id in :facilityIds"
			+ " group by t.facility.id")
	List<Object[]> countByFacilityIds(@Param("facilityIds") Collection<Long> facilityIds);

	long countByFacility_Id(Long facilityId);
}
