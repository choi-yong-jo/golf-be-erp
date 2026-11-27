package com.golf.erp.file;

import com.golf.erp.config.ErpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * LOCAL 스토리지: erp.file.storage-dir 아래 {storage_key} 경로에 저장한다.
 * 임시 파일(storageDir/.tmp)에 먼저 쓰고 같은 파일시스템 안에서 원자적으로 옮기므로, 받다 끊긴 파일이 최종 경로에 남지 않는다.
 * 모든 경로는 루트 밖으로 나갈 수 없게 검사한다(경로 조작 방지).
 */
@Component
class LocalFileStorage {
	private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
	private static final String DEFAULT_DIR = "./uploads";
	private static final String TEMP_DIR = ".tmp";

	/** 저장 결과: 최종 경로, 실제 바이트 수, SHA-256(16진수 64자) */
	record Stored(Path path, long size, String sha256) {
	}

	private final Path root;

	LocalFileStorage(ErpProperties props) {
		String dir = props.file() == null || props.file().storageDir() == null || props.file().storageDir().isBlank()
				? DEFAULT_DIR
				: props.file().storageDir();
		this.root = Paths.get(dir).toAbsolutePath().normalize();
	}

	/** 스트림을 끝까지 읽어 storageKey 위치에 저장하면서 SHA-256 을 계산한다. 실패하면 임시 파일을 지운다. */
	Stored store(InputStream in, String storageKey) throws IOException {
		Path target = resolve(storageKey);
		Files.createDirectories(target.getParent());
		Path tempDir = root.resolve(TEMP_DIR);
		Files.createDirectories(tempDir);
		Path temp = Files.createTempFile(tempDir, "upload-", ".part");
		try {
			MessageDigest md = sha256();
			long size;
			try (DigestInputStream din = new DigestInputStream(in, md); OutputStream out = Files.newOutputStream(temp)) {
				size = din.transferTo(out);
			}
			moveAtomically(temp, target);
			return new Stored(target, size, HexFormat.of().formatHex(md.digest()));
		} catch (IOException | RuntimeException e) {
			Files.deleteIfExists(temp);
			throw e;
		}
	}

	/** storage_key → 실제 경로. 루트 밖을 가리키면 거부한다. */
	Path resolve(String storageKey) {
		if (storageKey == null || storageKey.isBlank() || storageKey.indexOf('\0') >= 0)
			throw new IllegalArgumentException("저장 경로가 올바르지 않습니다.");
		Path p;
		try {
			p = root.resolve(storageKey).normalize();
		} catch (InvalidPathException e) {
			throw new IllegalArgumentException("저장 경로가 올바르지 않습니다.");
		}
		if (!p.startsWith(root) || p.equals(root) || p.startsWith(root.resolve(TEMP_DIR)))
			throw new IllegalArgumentException("저장 경로가 올바르지 않습니다.");
		return p;
	}

	/** 업로드 롤백 시 정리용. 실패해도 예외를 던지지 않는다. */
	void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			log.warn("업로드 정리 중 파일을 지우지 못했습니다: {}", path, e);
		}
	}

	/**
	 * 이미지 헤더만 읽어 가로·세로를 구한다(전체 디코딩 안 함). 읽을 수 없는 형식(예: JDK 기본 ImageIO 가 모르는 webp)이면 null.
	 */
	static int[] imageSize(Path path) {
		try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
			if (iis == null)
				return null;
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext())
				return null;
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, true, true);
				int w = reader.getWidth(0);
				int h = reader.getHeight(0);
				return w > 0 && h > 0 ? new int[] { w, h } : null;
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	private static void moveAtomically(Path from, Path to) throws IOException {
		try {
			Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(from, to);
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
