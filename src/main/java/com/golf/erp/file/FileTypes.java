package com.golf.erp.file;

import java.util.Locale;
import java.util.Map;

/**
 * 업로드 허용 확장자와 MIME 타입·분류. 클라이언트가 보낸 Content-Type 은 믿지 않고 확장자로 정한다.
 * 목록에 없는 확장자는 업로드할 수 없다.
 */
final class FileTypes {
	record Type(String mimeType, FileCategory category) {
	}

	private static final Map<String, Type> TYPES = Map.ofEntries(
			// 이미지
			Map.entry("jpg", new Type("image/jpeg", FileCategory.IMAGE)),
			Map.entry("jpeg", new Type("image/jpeg", FileCategory.IMAGE)),
			Map.entry("png", new Type("image/png", FileCategory.IMAGE)),
			Map.entry("gif", new Type("image/gif", FileCategory.IMAGE)),
			Map.entry("webp", new Type("image/webp", FileCategory.IMAGE)),
			// 문서
			Map.entry("pdf", new Type("application/pdf", FileCategory.DOCUMENT)),
			Map.entry("doc", new Type("application/msword", FileCategory.DOCUMENT)),
			Map.entry("docx", new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
					FileCategory.DOCUMENT)),
			Map.entry("xls", new Type("application/vnd.ms-excel", FileCategory.DOCUMENT)),
			Map.entry("xlsx", new Type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					FileCategory.DOCUMENT)),
			Map.entry("ppt", new Type("application/vnd.ms-powerpoint", FileCategory.DOCUMENT)),
			Map.entry("pptx", new Type("application/vnd.openxmlformats-officedocument.presentationml.presentation",
					FileCategory.DOCUMENT)),
			Map.entry("hwp", new Type("application/x-hwp", FileCategory.DOCUMENT)),
			Map.entry("hwpx", new Type("application/vnd.hancom.hwpx", FileCategory.DOCUMENT)),
			Map.entry("txt", new Type("text/plain", FileCategory.DOCUMENT)),
			Map.entry("csv", new Type("text/csv", FileCategory.DOCUMENT)),
			// 그 밖
			Map.entry("zip", new Type("application/zip", FileCategory.OTHER)));

	private FileTypes() {
	}

	/** 허용되지 않은 확장자면 null */
	static Type of(String extension) {
		return extension == null ? null : TYPES.get(extension.toLowerCase(Locale.ROOT));
	}

	static String allowedList() {
		return String.join(", ", TYPES.keySet().stream().sorted().toList());
	}
}
