package com.golf.erp.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<?> badRequest(IllegalArgumentException e) {
		return body(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<?> validation(MethodArgumentNotValidException e) {
		String m = e.getBindingResult().getFieldErrors().stream().findFirst()
				.map(x -> x.getField() + ": " + x.getDefaultMessage()).orElse("Invalid request");
		return body(HttpStatus.BAD_REQUEST, m);
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<?> unreadable(Exception e) {
		return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
	}

	/** 없는 경로·정적 파일(favicon.ico 등). 오류 로그를 남기지 않고 404 */
	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<?> noResource(NoResourceFoundException e) {
		return body(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다: /" + e.getResourcePath());
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<?> notFound(NotFoundException e) {
		return body(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<?> conflict(ConflictException e) {
		return body(HttpStatus.CONFLICT, e.getMessage());
	}

	/** 다른 사용자(또는 예약 앱)가 먼저 같은 데이터를 바꾼 경우 (@Version 충돌) */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ResponseEntity<?> optimisticLock(ObjectOptimisticLockingFailureException e) {
		return body(HttpStatus.CONFLICT, "다른 요청이 먼저 데이터를 변경했습니다. 다시 조회한 뒤 시도해 주세요.");
	}

	/** 행 잠금 대기(3초) 초과: 같은 예약/티타임을 다른 요청(예약 앱 포함)이 처리 중 */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<?> lockTimeout(PessimisticLockingFailureException e) {
		return body(HttpStatus.CONFLICT, "다른 요청이 같은 데이터를 처리 중입니다. 잠시 후 다시 시도해 주세요.");
	}

	/** 유니크/외래키/CHECK 제약 위반. 서비스 검증을 통과했지만 DB가 거부한 경우 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<?> integrity(DataIntegrityViolationException e) {
		log.warn("DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
		return body(HttpStatus.CONFLICT, "데이터 제약 조건에 맞지 않습니다. 중복되거나 허용되지 않는 값인지 확인해 주세요.");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<?> serverError(Exception e) {
		log.error("처리되지 않은 오류", e);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");
	}

	private static ResponseEntity<?> body(HttpStatus status, String message) {
		return ResponseEntity.status(status)
				.body(Map.of("timestamp", Instant.now(), "status", status.value(), "message", message));
	}
}
