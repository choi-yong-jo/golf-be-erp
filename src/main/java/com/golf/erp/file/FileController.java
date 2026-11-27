package com.golf.erp.file;

import com.golf.erp.common.PageResult;
import com.golf.erp.file.FileDtos.FileDownload;
import com.golf.erp.file.FileDtos.FileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** 공통 파일. /api/files 는 ErpModule 에 등록하지 않아 로그인한 직원이면 누구나 쓴다. */
@Tag(name = "공통 파일")
@RestController
@RequestMapping("/api/files")
public class FileController {
	private final FileService service;

	public FileController(FileService service) {
		this.service = service;
	}

	@Operation(summary = "파일 업로드", description = "multipart/form-data. files: 파일 1개 이상(최대 20개, 파일당 20MB). "
			+ "허용 확장자: jpg, jpeg, png, gif, webp / pdf, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, txt, csv / zip. "
			+ "category: 분류 직접 지정(선택, IMAGE 는 이미지 확장자만)")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public List<FileResponse> upload(@RequestPart(name = "files", required = false) List<MultipartFile> files,
			@RequestParam(required = false) FileCategory category) {
		return service.upload(files, category);
	}

	@Operation(summary = "파일 목록", description = "uploadStatus 를 생략하면 DELETED 를 뺀 파일. from/to: 업로드 일자. keyword: 원본 파일명")
	@GetMapping
	public PageResult<FileResponse> list(@RequestParam(required = false) FileCategory category,
			@RequestParam(required = false) FileUploadStatus uploadStatus,
			@RequestParam(required = false) Long uploaderAdminUserId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(category, uploadStatus, uploaderAdminUserId, from, to, keyword, page, size);
	}

	@GetMapping("/{id}")
	public FileResponse get(@PathVariable long id) {
		return service.get(id);
	}

	@Operation(summary = "파일 내려받기", description = "inline=true 이고 이미지면 브라우저에서 바로 표시, 그 밖에는 첨부 파일로 내려받는다. "
			+ "삭제됐거나 저장소에 파일이 없으면 404")
	@GetMapping("/{id}/download")
	public ResponseEntity<Resource> download(@PathVariable long id,
			@RequestParam(defaultValue = "false") boolean inline) {
		FileDownload d = service.download(id, inline);
		ContentDisposition disposition = (d.inline() ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(d.filename(), StandardCharsets.UTF_8).build();
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mimeType())).contentLength(d.size())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff")
				.header("Content-Security-Policy", "default-src 'none'; sandbox")
				.cacheControl(CacheControl.noCache().cachePrivate()).body(new FileSystemResource(d.path()));
	}

	@Operation(summary = "파일 삭제", description = "삭제 표시만 한다(실제 파일은 정리 배치가 지움). 올린 사람이나 ADMIN 만 가능, "
			+ "게시글에 첨부돼 있으면 409")
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		service.delete(id);
	}
}
