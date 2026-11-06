package com.knowflow.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Uniform API Error response structure per architecture spec.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        List<FieldErrorItem> fieldErrors,
        String correlationId,
        Instant timestamp
) {
    public static ApiError of(String code, String message, String correlationId, Instant timestamp) {
        return new ApiError(code, message, null, correlationId, timestamp);
    }

    public static ApiError of(String code, String message, List<FieldErrorItem> fieldErrors, String correlationId, Instant timestamp) {
        return new ApiError(code, message, fieldErrors, correlationId, timestamp);
    }

    public record FieldErrorItem(String field, String message) {}
}
