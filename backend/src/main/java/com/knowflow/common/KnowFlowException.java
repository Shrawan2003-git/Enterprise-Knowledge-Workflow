package com.knowflow.common;

/**
 * Base domain and runtime exception for KnowFlow with structured error codes and HTTP status mapping.
 */
public class KnowFlowException extends RuntimeException {

    private final String errorCode;
    private final int statusCode;

    public KnowFlowException(String errorCode, String message, int statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public KnowFlowException(String errorCode, String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
