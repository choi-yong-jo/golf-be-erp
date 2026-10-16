package com.golf.erp.common;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<?> badRequest(IllegalArgumentException e) {
		return ResponseEntity.badRequest()
				.body(Map.of("timestamp", Instant.now(), "status", 400, "message", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<?> validation(MethodArgumentNotValidException e) {
		String m = e.getBindingResult().getFieldErrors().stream().findFirst()
				.map(x -> x.getField() + ": " + x.getDefaultMessage()).orElse("Invalid request");
		return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "status", 400, "message", m));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<?> serverError(Exception e) {
		return ResponseEntity.status(500)
				.body(Map.of("timestamp", Instant.now(), "status", 500, "message", "서버 내부 오류가 발생했습니다."));
	}
}
