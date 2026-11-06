package com.knowflow.ingestion;

public class IngestionException extends RuntimeException {

    private final IngestionErrorCode errorCode;

    public IngestionException(IngestionErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
    }

    public IngestionException(IngestionErrorCode errorCode, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
    }

    public IngestionErrorCode getErrorCode() {
        return errorCode;
    }
}
