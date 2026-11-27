package com.golf.erp.file;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * golf.common_file — 업로드 파일 메타데이터. 실제 바이너리는 스토리지(storage_provider/storage_key)에 있다.
 * ERP 에서는 직원 업로드만 만든다(uploaded_by_admin_user_id). uploaded_by_member_id 는 예약 앱(고객) 업로드용이다.
 */
@Entity
@Table(name = "common_file")
public class CommonFile {
	static final String LOCAL = "LOCAL";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "common_file_id")
	private Long id;
	@Column(name = "storage_provider", nullable = false, length = 30)
	private String storageProvider = LOCAL;
	@Column(name = "storage_bucket", length = 150)
	private String storageBucket;
	@Column(name = "storage_key", nullable = false, unique = true, length = 1000)
	private String storageKey;
	@Column(name = "original_filename", nullable = false, length = 500)
	private String originalFilename;
	@Column(name = "stored_filename", length = 255)
	private String storedFilename;
	@Column(name = "file_extension", length = 30)
	private String fileExtension;
	@Column(name = "mime_type", nullable = false, length = 150)
	private String mimeType;
	@Enumerated(EnumType.STRING)
	@Column(name = "file_category", nullable = false, length = 20)
	private FileCategory fileCategory;
	@Column(name = "file_size_bytes", nullable = false)
	private Long fileSizeBytes;
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "checksum_sha256", length = 64)
	private String checksumSha256;
	@Column(name = "image_width")
	private Integer imageWidth;
	@Column(name = "image_height")
	private Integer imageHeight;
	@Enumerated(EnumType.STRING)
	@Column(name = "upload_status", nullable = false, length = 20)
	private FileUploadStatus uploadStatus = FileUploadStatus.ACTIVE;
	@Column(name = "uploaded_by_member_id")
	private Long uploadedByMemberId;
	@Column(name = "uploaded_by_admin_user_id")
	private Long uploadedByAdminUserId;
	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private LocalDateTime uploadedAt;
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected CommonFile() {
	}

	/**
	 * 직원이 LOCAL 스토리지에 올린 파일.
	 * 이미지 크기는 둘 다 있거나 둘 다 null 이어야 한다(ck_common_file_image_size).
	 */
	public CommonFile(String storageKey, String originalFilename, String storedFilename, String fileExtension,
			String mimeType, FileCategory fileCategory, long fileSizeBytes, String checksumSha256, Integer imageWidth,
			Integer imageHeight, long uploadedByAdminUserId, LocalDateTime now) {
		if (fileSizeBytes <= 0)
			throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
		boolean hasSize = imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0;
		this.storageKey = storageKey;
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.fileExtension = fileExtension;
		this.mimeType = mimeType;
		this.fileCategory = fileCategory;
		this.fileSizeBytes = fileSizeBytes;
		this.checksumSha256 = checksumSha256;
		this.imageWidth = hasSize ? imageWidth : null;
		this.imageHeight = hasSize ? imageHeight : null;
		this.uploadedByAdminUserId = uploadedByAdminUserId;
		this.uploadedAt = now;
	}

	/** 삭제 표시. 실제 파일은 지우지 않는다(정리 배치가 DELETED 파일을 나중에 지운다). 이미 삭제됐으면 false */
	public boolean markDeleted(LocalDateTime now) {
		if (uploadStatus == FileUploadStatus.DELETED)
			return false;
		this.uploadStatus = FileUploadStatus.DELETED;
		this.deletedAt = now;
		return true;
	}

	public Long getId() {
		return id;
	}

	public String getStorageProvider() {
		return storageProvider;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getStoredFilename() {
		return storedFilename;
	}

	public String getFileExtension() {
		return fileExtension;
	}

	public String getMimeType() {
		return mimeType;
	}

	public FileCategory getFileCategory() {
		return fileCategory;
	}

	public Long getFileSizeBytes() {
		return fileSizeBytes;
	}

	public String getChecksumSha256() {
		return checksumSha256;
	}

	public Integer getImageWidth() {
		return imageWidth;
	}

	public Integer getImageHeight() {
		return imageHeight;
	}

	public FileUploadStatus getUploadStatus() {
		return uploadStatus;
	}

	public Long getUploadedByMemberId() {
		return uploadedByMemberId;
	}

	public Long getUploadedByAdminUserId() {
		return uploadedByAdminUserId;
	}

	public LocalDateTime getUploadedAt() {
		return uploadedAt;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}
}
