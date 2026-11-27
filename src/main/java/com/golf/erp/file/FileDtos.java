package com.golf.erp.file;

import java.nio.file.Path;
import java.time.LocalDateTime;

/** 파일 API 요청/응답 */
public final class FileDtos {
	private FileDtos() {
	}

	/**
	 * 파일 메타데이터. 저장 위치(storage_key)는 내부 정보라 내보내지 않는다. 내려받기는 downloadUrl 로 한다(Bearer 토큰 필요).
	 */
	public record FileResponse(Long id, String originalFilename, String storedFilename, String fileExtension,
			String mimeType, FileCategory fileCategory, Long fileSizeBytes, String checksumSha256, Integer imageWidth,
			Integer imageHeight, FileUploadStatus uploadStatus, String storageProvider, Long uploadedByMemberId,
			Long uploadedByAdminUserId, LocalDateTime uploadedAt, LocalDateTime deletedAt, String downloadUrl) {

		static FileResponse of(CommonFile f) {
			return new FileResponse(f.getId(), f.getOriginalFilename(), f.getStoredFilename(), f.getFileExtension(),
					f.getMimeType(), f.getFileCategory(), f.getFileSizeBytes(), f.getChecksumSha256(),
					f.getImageWidth(), f.getImageHeight(), f.getUploadStatus(), f.getStorageProvider(),
					f.getUploadedByMemberId(), f.getUploadedByAdminUserId(), f.getUploadedAt(), f.getDeletedAt(),
					"/api/files/" + f.getId() + "/download");
		}
	}

	/** 내려받기 대상(컨트롤러가 스트리밍 응답으로 만든다). API 응답 본문이 아니다. */
	public record FileDownload(Path path, String mimeType, String filename, long size, boolean inline) {
	}
}
