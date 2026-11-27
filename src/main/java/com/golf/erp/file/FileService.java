package com.golf.erp.file;

import com.golf.erp.common.AuditLogger;
import com.golf.erp.common.ConflictException;
import com.golf.erp.common.NotFoundException;
import com.golf.erp.common.PageResult;
import com.golf.erp.common.Pages;
import com.golf.erp.common.Specs;
import com.golf.erp.common.UnauthorizedException;
import com.golf.erp.file.FileDtos.FileDownload;
import com.golf.erp.file.FileDtos.FileResponse;
import com.golf.erp.security.AdminPrincipal;
import com.golf.erp.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 공통 파일 업로드·조회·내려받기·삭제. 로그인한 직원이면 누구나 쓴다(ErpModule 에 등록하지 않음).
 * <p>
 * 삭제는 삭제 표시(upload_status = DELETED)만 하고 실제 파일은 남긴다. 스토리지 정리 배치가
 * DELETED 이면서 deleted_at 이 충분히 지난 파일을 나중에 지우는 것을 전제로 한다.
 */
@Service
@Transactional
public class FileService {
	/** 한 번에 올릴 수 있는 파일 수(파일당 크기는 spring.servlet.multipart.max-file-size 로 제한) */
	static final int MAX_FILES_PER_REQUEST = 20;
	private static final int MAX_ORIGINAL_NAME = 500;
	private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	private final CommonFileRepository repo;
	private final LocalFileStorage storage;
	private final FileUsageLookup usage;
	private final AuditLogger audit;
	private final Clock clock;

	public FileService(CommonFileRepository repo, LocalFileStorage storage, FileUsageLookup usage, AuditLogger audit,
			Clock clock) {
		this.repo = repo;
		this.storage = storage;
		this.usage = usage;
		this.audit = audit;
		this.clock = clock;
	}

	/** 업로드 전 검증을 마친 파일 */
	private record Incoming(MultipartFile part, String originalFilename, String extension, String mimeType,
			FileCategory category) {
	}

	/**
	 * 파일을 저장하고 메타데이터를 등록한다. 모든 파일을 먼저 검증한 뒤 저장하고, 트랜잭션이 커밋되지 않으면(DB 오류 등)
	 * 이번 요청에서 저장한 실제 파일을 지운다.
	 *
	 * @param categoryOverride 분류를 직접 지정(선택). IMAGE 는 이미지 확장자에만 쓸 수 있다.
	 */
	public List<FileResponse> upload(List<MultipartFile> parts, FileCategory categoryOverride) {
		Long uploader = CurrentUser.idOrNull();
		if (uploader == null)
			throw new UnauthorizedException("로그인이 필요합니다.");
		if (parts == null || parts.isEmpty())
			throw new IllegalArgumentException("업로드할 파일(files)을 선택하세요.");
		if (parts.size() > MAX_FILES_PER_REQUEST)
			throw new IllegalArgumentException("파일은 한 번에 " + MAX_FILES_PER_REQUEST + "개까지 올릴 수 있습니다.");
		List<Incoming> incoming = parts.stream().map(p -> inspect(p, categoryOverride)).toList();

		List<Path> written = new ArrayList<>();
		cleanupOnRollback(written);
		LocalDateTime now = now();
		List<FileResponse> result = new ArrayList<>();
		for (Incoming in : incoming) {
			String storedFilename = UUID.randomUUID() + "." + in.extension();
			String storageKey = now.toLocalDate().format(KEY_DATE) + "/" + storedFilename;
			LocalFileStorage.Stored stored;
			try (InputStream stream = in.part().getInputStream()) {
				stored = storage.store(stream, storageKey);
			} catch (IOException e) {
				throw new UncheckedIOException("파일 저장 실패: " + in.originalFilename(), e);
			}
			written.add(stored.path());
			if (stored.size() <= 0)
				throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다: " + in.originalFilename());

			Integer width = null;
			Integer height = null;
			if (in.category() == FileCategory.IMAGE) {
				int[] size = LocalFileStorage.imageSize(stored.path());
				if (size != null) {
					width = size[0];
					height = size[1];
				}
			}
			CommonFile f = new CommonFile(storageKey, in.originalFilename(), storedFilename, in.extension(),
					in.mimeType(), in.category(), stored.size(), stored.sha256(), width, height, uploader, now);
			result.add(FileResponse.of(repo.save(f)));
		}
		repo.flush();
		return result;
	}

	/**
	 * @param uploadStatus 생략하면 DELETED 를 뺀 파일만
	 */
	@Transactional(readOnly = true)
	public PageResult<FileResponse> list(FileCategory category, FileUploadStatus uploadStatus,
			Long uploaderAdminUserId, LocalDate from, LocalDate to, String keyword, int page, int size) {
		Specification<CommonFile> spec = Specification.allOf(Specs.eq("fileCategory", category),
				Specs.eq("uploadStatus", uploadStatus), Specs.eq("uploadedByAdminUserId", uploaderAdminUserId),
				Specs.gte("uploadedAt", from == null ? null : from.atStartOfDay()),
				Specs.lt("uploadedAt", to == null ? null : to.plusDays(1).atStartOfDay()),
				Specs.like(keyword, "originalFilename"));
		if (uploadStatus == null)
			spec = spec.and((r, q, cb) -> cb.notEqual(r.get("uploadStatus"), FileUploadStatus.DELETED));
		Sort sort = Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"));
		return PageResult.of(repo.findAll(spec, Pages.of(page, size, sort)), FileResponse::of);
	}

	@Transactional(readOnly = true)
	public FileResponse get(long id) {
		return FileResponse.of(find(id));
	}

	/**
	 * 내려받기 대상. 삭제됐거나 스토리지에 파일이 없으면 404, 격리된 파일은 409.
	 *
	 * @param inline true 이고 이미지면 브라우저에서 바로 보이게(inline), 그 밖에는 항상 첨부(attachment)
	 */
	@Transactional(readOnly = true)
	public FileDownload download(long id, boolean inline) {
		CommonFile f = find(id);
		if (f.getUploadStatus() == FileUploadStatus.DELETED)
			throw NotFoundException.of("파일", id);
		if (f.getUploadStatus() == FileUploadStatus.QUARANTINED)
			throw new ConflictException("격리된 파일은 내려받을 수 없습니다.");
		if (!CommonFile.LOCAL.equals(f.getStorageProvider()))
			throw new ConflictException("이 서버에서 내려받을 수 없는 저장소의 파일입니다: " + f.getStorageProvider());
		Path path = storage.resolve(f.getStorageKey());
		if (!Files.isRegularFile(path))
			throw new NotFoundException("저장소에서 파일을 찾을 수 없습니다: " + id);
		long size;
		try {
			size = Files.size(path);
		} catch (IOException e) {
			throw new NotFoundException("저장소에서 파일을 찾을 수 없습니다: " + id);
		}
		String mime = safeMimeType(f.getMimeType());
		boolean asInline = inline && mime.startsWith("image/");
		return new FileDownload(path, mime, f.getOriginalFilename(), size, asInline);
	}

	/**
	 * 삭제 표시. 올린 사람 본인이나 ADMIN 만 할 수 있다(그 밖에는 403). 삭제되지 않은 게시글에 첨부돼 있으면 409.
	 * 이미 삭제된 파일이면 아무것도 하지 않는다.
	 */
	public void delete(long id) {
		AdminPrincipal me = CurrentUser.get().orElseThrow(() -> new UnauthorizedException("로그인이 필요합니다."));
		CommonFile f = repo.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("파일", id));
		if (!me.isAdmin() && !Objects.equals(f.getUploadedByAdminUserId(), me.id()))
			throw new AccessDeniedException("본인이 올린 파일만 삭제할 수 있습니다.");
		if (f.getUploadStatus() == FileUploadStatus.DELETED)
			return;
		Long boardId = usage.activeBoardUsing(id);
		if (boardId != null)
			throw new ConflictException("게시글(" + boardId + ")에 첨부된 파일입니다. 게시글에서 첨부를 먼저 해제하세요.");
		String menuName = usage.menuItemUsing(id);
		if (menuName != null)
			throw new ConflictException("매장 메뉴(" + menuName + ") 이미지로 쓰이는 파일입니다. 메뉴 이미지를 먼저 바꾸세요.");
		FileUploadStatus before = f.getUploadStatus();
		f.markDeleted(now());
		audit.log("DELETE", "common_file", id,
				Map.of("originalFilename", f.getOriginalFilename(), "before", before, "storageKey", f.getStorageKey()));
	}

	private Incoming inspect(MultipartFile part, FileCategory override) {
		if (part == null || part.isEmpty())
			throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다.");
		String name = originalName(part.getOriginalFilename());
		if (name == null)
			throw new IllegalArgumentException("파일 이름이 없습니다.");
		String ext = extension(name);
		FileTypes.Type type = FileTypes.of(ext);
		if (type == null)
			throw new IllegalArgumentException(
					"업로드할 수 없는 파일 형식입니다: " + name + " (허용: " + FileTypes.allowedList() + ")");
		FileCategory category = type.category();
		if (override != null) {
			if (override == FileCategory.IMAGE && type.category() != FileCategory.IMAGE)
				throw new IllegalArgumentException("이미지 형식이 아닌 파일은 IMAGE 로 분류할 수 없습니다: " + name);
			category = override;
		}
		return new Incoming(part, name, ext, type.mimeType(), category);
	}

	/** 경로 부분(디렉터리)과 제어 문자를 없앤 파일 이름. 500자를 넘으면 확장자를 살려 자른다. 이름이 없으면 null */
	static String originalName(String raw) {
		if (raw == null)
			return null;
		String n = raw;
		int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
		if (slash >= 0)
			n = n.substring(slash + 1);
		n = n.replaceAll("\\p{Cntrl}", "").strip();
		if (n.isEmpty() || n.equals(".") || n.equals(".."))
			return null;
		if (n.length() > MAX_ORIGINAL_NAME) {
			int dot = n.lastIndexOf('.');
			String ext = dot >= 0 && n.length() - dot <= 31 ? n.substring(dot) : "";
			n = n.substring(0, MAX_ORIGINAL_NAME - ext.length()) + ext;
		}
		return n;
	}

	/** 소문자 확장자(점 제외). 없으면 null */
	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1)
			return null;
		return name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String safeMimeType(String mime) {
		try {
			return MediaType.parseMediaType(mime).toString();
		} catch (IllegalArgumentException e) { // InvalidMediaTypeException 포함
			return MediaType.APPLICATION_OCTET_STREAM_VALUE;
		}
	}

	/** 트랜잭션이 커밋되지 않으면 이번 요청에서 저장한 파일을 지운다. */
	private void cleanupOnRollback(List<Path> written) {
		if (!TransactionSynchronizationManager.isSynchronizationActive())
			return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED)
					written.forEach(storage::deleteQuietly);
			}
		});
	}

	private CommonFile find(long id) {
		return repo.findById(id).orElseThrow(() -> NotFoundException.of("파일", id));
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
