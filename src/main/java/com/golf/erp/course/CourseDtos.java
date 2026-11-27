package com.golf.erp.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 골프장·휴장·코스·홀 API 요청/응답 */
public final class CourseDtos {
	private CourseDtos() {
	}

	// ---------- 골프장 ----------

	/** holeCount 생략 시 18 */
	public record GolfCourseRequest(@NotBlank @Size(max = 30) String courseCode,
			@NotBlank @Size(max = 200) String courseName, @Size(max = 500) String address,
			@Size(max = 30) String phone, @Min(1) @Max(72) Integer holeCount) {
	}

	/** active=false 면 비활성(미사용) 처리. 생략하면 현재 값 유지 */
	public record GolfCourseStatusRequest(@NotNull GolfCourseStatus status, Boolean active) {
	}

	/** operating: 예약·티타임 생성 가능 여부(활성이면서 OPEN) */
	public record GolfCourseResponse(Long id, String courseCode, String courseName, String address, String phone,
			Integer holeCount, GolfCourseStatus status, boolean active, boolean operating, LocalDateTime createdAt,
			LocalDateTime updatedAt) {

		static GolfCourseResponse of(GolfCourse c) {
			return new GolfCourseResponse(c.getId(), c.getCourseCode(), c.getCourseName(), c.getAddress(),
					c.getPhone(), c.getHoleCount(), c.getStatus(), c.isActive(), c.isOperating(), c.getCreatedAt(),
					c.getUpdatedAt());
		}
	}

	// ---------- 휴장 ----------

	/** closureStart 포함, closureEnd 미포함. closureType 생략 시 CLOSED */
	public record ClosureRequest(@NotNull LocalDateTime closureStart, @NotNull LocalDateTime closureEnd,
			@Size(max = 20) String closureType, @NotBlank @Size(max = 500) String reason) {
	}

	/**
	 * activeReservationCount: 등록 시점에 휴장 구간에 걸린 활성 예약 수(등록 응답에만, 목록에서는 null).
	 * 휴장을 등록해도 기존 예약은 자동 취소되지 않으므로 화면에서 안내한다.
	 */
	public record ClosureResponse(Long id, Long golfCourseId, LocalDateTime closureStart, LocalDateTime closureEnd,
			String closureType, String reason, boolean active, Integer activeReservationCount) {

		static ClosureResponse of(GolfCourseClosure c, Integer activeReservationCount) {
			return new ClosureResponse(c.getId(), c.getGolfCourseId(), c.getClosureStart(), c.getClosureEnd(),
					c.getClosureType(), c.getReason(), c.isActive(), activeReservationCount);
		}
	}

	// ---------- 코스 ----------

	/** courseCode 는 대문자로 저장되며 티타임의 course 값으로 쓰인다(예: OUT, IN). holeCount 생략 시 9 */
	public record CourseCreateRequest(@NotNull Long golfCourseId, @NotBlank @Size(max = 20) String courseCode,
			@NotBlank @Size(max = 100) String courseName, @Min(1) @Max(18) Short holeCount,
			@Min(0) @Max(999) Short sortOrder) {
	}

	public record CourseUpdateRequest(@NotBlank @Size(max = 20) String courseCode,
			@NotBlank @Size(max = 100) String courseName, @NotNull @Min(1) @Max(18) Short holeCount,
			@Min(0) @Max(999) Short sortOrder) {
	}

	/** active 생략 시 현재 값 유지(비활성 코스를 다시 쓰려면 active=true) */
	public record CourseStatusRequest(@NotNull CourseStatus status, Boolean active) {
	}

	/** totalPar·registeredHoles: 등록된 홀 기준 합계 */
	public record CourseResponse(Long id, Long golfCourseId, String courseCode, String courseName, Short holeCount,
			Short sortOrder, CourseStatus status, boolean active, int totalPar, int registeredHoles,
			LocalDateTime createdAt, LocalDateTime updatedAt) {

		static CourseResponse of(Course c, HoleSummary s) {
			return new CourseResponse(c.getId(), c.getGolfCourseId(), c.getCourseCode(), c.getCourseName(),
					c.getHoleCount(), c.getSortOrder(), c.getStatus(), c.isActive(), s.totalPar(), s.holes(),
					c.getCreatedAt(), c.getUpdatedAt());
		}
	}

	/** 코스별 홀 집계(내부용) */
	record HoleSummary(int totalPar, int holes, int maxHoleNo) {
		static final HoleSummary EMPTY = new HoleSummary(0, 0, 0);
	}

	// ---------- 홀 ----------

	/** 스코어카드 한 줄. distance* 단위 m */
	public record HoleRequest(@NotNull @Min(1) @Max(18) Short holeNo, @NotNull @Min(3) @Max(6) Short par,
			@Min(1) @Max(18) Short handicap, @Positive Integer distanceBack, @Positive Integer distanceRegular,
			@Positive Integer distanceFront, @Size(max = 500) String memo) {
	}

	/** 스코어카드 일괄 저장. 보낸 홀 번호만 등록·수정하고, 보내지 않은 기존 홀은 그대로 둔다. */
	public record HoleBulkRequest(@NotEmpty @Size(max = 18) List<@Valid @NotNull HoleRequest> holes) {
	}

	public record HoleUpdateRequest(@NotNull @Min(3) @Max(6) Short par, @Min(1) @Max(18) Short handicap,
			@Positive Integer distanceBack, @Positive Integer distanceRegular, @Positive Integer distanceFront,
			@Size(max = 500) String memo) {
	}

	public record HoleResponse(Long id, Long courseId, Short holeNo, Short par, Short handicap, Integer distanceBack,
			Integer distanceRegular, Integer distanceFront, String memo, LocalDateTime updatedAt) {

		static HoleResponse of(Hole h) {
			return new HoleResponse(h.getId(), h.getCourseId(), h.getHoleNo(), h.getPar(), h.getHandicap(),
					h.getDistanceBack(), h.getDistanceRegular(), h.getDistanceFront(), h.getMemo(), h.getUpdatedAt());
		}
	}

	/** 코스 스코어카드: 홀 목록과 파 합계 */
	public record ScorecardResponse(Long courseId, String courseCode, String courseName, Short holeCount,
			int totalPar, List<HoleResponse> holes) {
	}
}
