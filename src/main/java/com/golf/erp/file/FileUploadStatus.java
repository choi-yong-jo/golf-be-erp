package com.golf.erp.file;

/**
 * common_file.upload_status.
 * <ul>
 * <li>ACTIVE: 사용 가능</li>
 * <li>QUARANTINED: 격리(검사 대기·의심 파일). 내려받기·첨부 불가</li>
 * <li>DELETED: 삭제 표시. 실제 파일은 남아 있고, 정리 배치가 나중에 지운다</li>
 * </ul>
 */
public enum FileUploadStatus {
	ACTIVE, QUARANTINED, DELETED
}
