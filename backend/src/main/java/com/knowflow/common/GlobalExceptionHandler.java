package com.knowflow.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Intercepts uncaught exceptions and shapes them into consistent {@link ApiError} payloads
 * without leaking internal stack traces or database details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    private String getCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return correlationId != null ? correlationId : "unknown";
    }

    @ExceptionHandler(KnowFlowException.class)
    public ResponseEntity<ApiError> handleKnowFlowException(KnowFlowException ex) {
        ApiError error = ApiError.of(
                ex.getErrorCode(),
                ex.getMessage(),
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<ApiError.FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ApiError.FieldErrorItem(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ApiError error = ApiError.of(
                "VALIDATION_ERROR",
                "Input validation failed",
                fieldErrors,
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        ApiError error = ApiError.of(
                "INVALID_CREDENTIALS",
                "Invalid email or password",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(AuthenticationException ex) {
        ApiError error = ApiError.of(
                "UNAUTHENTICATED",
                "Authentication required",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        ApiError error = ApiError.of(
                "FORBIDDEN",
                "Access is denied",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        ApiError error = ApiError.of(
                "BAD_REQUEST",
                ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        ApiError error = ApiError.of(
                "CONFLICT",
                ex.getMessage() != null ? ex.getMessage() : "State conflict occurred",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException ex) {
        ApiError error = ApiError.of(
                "NOT_FOUND",
                ex.getMessage() != null ? ex.getMessage() : "Resource not found",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        log.error("Unhandled server exception [correlationId={}]", getCorrelationId(), ex);

        ApiError error = ApiError.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred",
                getCorrelationId(),
                Instant.now(clock)
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
