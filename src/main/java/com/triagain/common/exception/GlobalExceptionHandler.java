package com.triagain.common.exception;

import com.triagain.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageSource messageSource;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
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
            if (constraintName.contains("upload_session_id")) {
                errorCode = ErrorCode.UPLOAD_SESSION_ALREADY_USED;
            } else if (constraintName.contains("verification")) {
                errorCode = ErrorCode.VERIFICATION_ALREADY_EXISTS;
            }
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
