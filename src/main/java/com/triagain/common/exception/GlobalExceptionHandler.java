package com.triagain.common.exception;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.triagain.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private final MessageSource messageSource;

	/**
	 * DB 제약 이름 → 에러코드 정확 매칭. {@code contains()} 부분매칭 금지 — 새 제약이 추가돼도 조용히
	 * 오매핑되지 않는다. 키는 마이그레이션 파일에서 그대로 복사한 값이다(추론 금지, lessons-learned.md).
	 * 미등록 제약은 {@link ErrorCode#DATA_CONFLICT}로 폴백한다.
	 */
	private static final Map<String, ErrorCode> CONSTRAINT_ERRORS = Map.of(
			"uk_verifications_user_crew_date_active", ErrorCode.VERIFICATION_ALREADY_EXISTS,
			"uk_verifications_upload_session",        ErrorCode.UPLOAD_SESSION_ALREADY_USED,
			"uk_reports_verification_reporter",       ErrorCode.REPORT_ALREADY_EXISTS,
			"uk_habit_verifications_upload_session",  ErrorCode.UPLOAD_SESSION_ALREADY_USED,
			"uk_habit_verifications_habit_date",      ErrorCode.HABIT_VERIFICATION_ALREADY_EXISTS
	);

	/** ErrorCode → properties 메시지 resolve */
	private String resolveMessage(ErrorCode errorCode, Object[] args) {
		return messageSource.getMessage(
				errorCode.name(), args, errorCode.name(), Locale.getDefault());
	}

	@ExceptionHandler(BusinessException.class)
	protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
		ErrorCode errorCode = e.getErrorCode();
		String message = resolveMessage(errorCode, e.getArgs());
		log.warn("[{} {}] 비즈니스 예외 [errorCode={}]: {}", request.getMethod(), request.getRequestURI(), errorCode.getCode(), message);
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ApiResponse.fail(errorCode, message));
	}

	/** Validation 예외 — message가 ErrorCode name이면 해당 코드 사용, 아니면 C001 INVALID_INPUT */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	protected ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
		List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();

		for (FieldError error : fieldErrors) {
			try {
				ErrorCode mappedCode = ErrorCode.valueOf(error.getDefaultMessage());
				String message = resolveMessage(mappedCode, null);
				log.warn("[{} {}] 입력값 검증 실패 [errorCode={}]: {}", request.getMethod(), request.getRequestURI(), mappedCode.getCode(), message);
				return ResponseEntity.status(mappedCode.getStatus())
						.body(ApiResponse.fail(mappedCode, message));
			} catch (IllegalArgumentException ignored) {
			}
		}

		String message = fieldErrors.stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.reduce((a, b) -> a + ", " + b)
				.orElse("잘못된 입력값입니다.");

		log.warn("[{} {}] 입력값 검증 실패: {}", request.getMethod(), request.getRequestURI(), message);
		return ResponseEntity
				.badRequest()
				.body(ApiResponse.fail(ErrorCode.INVALID_INPUT, message));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	protected ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
			DataIntegrityViolationException e, HttpServletRequest request) {
		ErrorCode errorCode = ErrorCode.DATA_CONFLICT;
		String constraintName = "unknown";

		Throwable cause = e.getCause();
		if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
			constraintName = cve.getConstraintName() != null ? cve.getConstraintName() : "unknown";
			errorCode = CONSTRAINT_ERRORS.getOrDefault(constraintName, ErrorCode.DATA_CONFLICT);
		}

		log.warn("데이터 무결성 위반 [{} {}, constraint={}, errorCode={}]",
				request.getMethod(), request.getRequestURI(), constraintName, errorCode.getCode());
		String message = resolveMessage(errorCode, null);
		return ResponseEntity
				.status(errorCode.getStatus())
				.body(ApiResponse.fail(errorCode, message));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	protected ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
		log.warn("[{} {}] 잘못된 인자: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
		return ResponseEntity
				.badRequest()
				.body(ApiResponse.fail(ErrorCode.INVALID_INPUT, e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
		log.error("[{} {}] 처리되지 않은 예외: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
		String message = resolveMessage(ErrorCode.INTERNAL_SERVER_ERROR, null);
		return ResponseEntity
				.internalServerError()
				.body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR, message));
	}
}
