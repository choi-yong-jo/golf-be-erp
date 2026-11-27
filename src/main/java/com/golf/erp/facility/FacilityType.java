package com.golf.erp.facility;

/**
 * 부대시설 유형(facility.facility_type, CHECK: MEETING_ROOM, BANQUET_HALL).
 * 둘 다 시간 단위 요금(공간 종류의 시간당 기본요금 × 이용 시간)이다. 숙박은 별도 lodging 모듈이 맡는다.
 */
public enum FacilityType {
	MEETING_ROOM, BANQUET_HALL
}
