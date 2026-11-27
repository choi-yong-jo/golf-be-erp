package com.golf.erp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 오류 응답 공통 형식: {timestamp, status, message} */
@RestControllerAdvice
public class ApiExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
		return body(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
		String m = e.getBindingResult().getFieldErrors().stream().findFirst()
				.map(x -> x.getField() + ": " + x.getDefaultMessage()).orElse("요청 값이 올바르지 않습니다.");
		return body(HttpStatus.BAD_REQUEST, m);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<Map<String, Object>> methodValidation(HandlerMethodValidationException e) {
		String m = e.getAllErrors().stream().findFirst().map(x -> x.getDefaultMessage())
				.orElse("요청 값이 올바르지 않습니다.");
		return body(HttpStatus.BAD_REQUEST, m);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException e) {
		return body(HttpStatus.BAD_REQUEST, "필수 파라미터가 없습니다: " + e.getParameterName());
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<Map<String, Object>> unreadable(Exception e) {
		return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<Map<String, Object>> uploadTooLarge(MaxUploadSizeExceededException e) {
		return body(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다. 파일당 20MB, 요청당 100MB 까지 올릴 수 있습니다.");
	}

	@ExceptionHandler({ MultipartException.class, MissingServletRequestPartException.class })
	ResponseEntity<Map<String, Object>> badMultipart(Exception e) {
		return body(HttpStatus.BAD_REQUEST, "파일 업로드 요청 형식이 올바르지 않습니다(multipart/form-data, files 필드).");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<Map<String, Object>> methodNotAllowed(HttpRequestMethodNotSupportedException e) {
		return body(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 메서드입니다: " + e.getMethod());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e) {
		return body(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다: /" + e.getResourcePath());
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
		return body(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
		return body(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(UnauthorizedException.class)
	ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException e) {
		return body(HttpStatus.UNAUTHORIZED, e.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException e) {
		return body(HttpStatus.FORBIDDEN, "권한이 없습니다.");
	}

	/** 다른 사용자(또는 예약 앱)가 먼저 같은 데이터를 바꾼 경우 (@Version 충돌) */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<Map<String, Object>> optimisticLock(ObjectOptimisticLockingFailureException e) {
		return body(HttpStatus.CONFLICT, "다른 요청이 먼저 데이터를 변경했습니다. 다시 조회한 뒤 시도해 주세요.");
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<Map<String, Object>> lockTimeout(PessimisticLockingFailureException e) {
		return body(HttpStatus.CONFLICT, "다른 요청이 같은 데이터를 처리 중입니다. 잠시 후 다시 시도해 주세요.");
	}

	/** 유니크/외래키/CHECK 제약 위반. 서비스 검증을 통과했지만 DB가 거부한 경우 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
		log.warn("DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
		return body(HttpStatus.CONFLICT, "데이터 제약 조건에 맞지 않습니다. 중복되거나 허용되지 않는 값인지 확인해 주세요.");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, Object>> serverError(Exception e) {
		log.error("처리되지 않은 오류", e);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
	}

	public static Map<String, Object> errorBody(HttpStatus status, String message) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("timestamp", Instant.now().toString());
		m.put("status", status.value());
		m.put("message", message);
		return m;
	}

	private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(errorBody(status, message));
	}
}
