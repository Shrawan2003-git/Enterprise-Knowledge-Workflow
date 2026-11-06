package com.knowflow.ingestion;

import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.TimeoutException;

@Component
public class ErrorClassifier {

    public record ClassifiedError(
            IngestionErrorCode code,
            String safeMessage,
            ErrorClassification classification
    ) {}

    public ClassifiedError classify(Throwable throwable) {
        if (throwable == null) {
            return new ClassifiedError(IngestionErrorCode.UNKNOWN_ERROR, "Unknown failure", ErrorClassification.TRANSIENT);
        }

        if (throwable instanceof IngestionException ie) {
            return new ClassifiedError(ie.getErrorCode(), ie.getMessage(), ie.getErrorCode().getDefaultClassification());
        }

        if (throwable instanceof FileNotFoundException || throwable instanceof NoSuchFileException) {
            return new ClassifiedError(IngestionErrorCode.FILE_NOT_FOUND, IngestionErrorCode.FILE_NOT_FOUND.getDescription(), ErrorClassification.PERMANENT);
        }

        if (throwable instanceof TimeoutException) {
            return new ClassifiedError(IngestionErrorCode.PARSER_TIMEOUT, IngestionErrorCode.PARSER_TIMEOUT.getDescription(), ErrorClassification.TRANSIENT);
        }

        String msg = throwable.getMessage() != null ? throwable.getMessage().toLowerCase() : "";
        if (msg.contains("encrypted") || msg.contains("password")) {
            return new ClassifiedError(IngestionErrorCode.PDF_ENCRYPTED, IngestionErrorCode.PDF_ENCRYPTED.getDescription(), ErrorClassification.PERMANENT);
        }

        if (msg.contains("timeout") || msg.contains("timed out")) {
            return new ClassifiedError(IngestionErrorCode.PARSER_TIMEOUT, IngestionErrorCode.PARSER_TIMEOUT.getDescription(), ErrorClassification.TRANSIENT);
        }

        if (msg.contains("eof") || msg.contains("bad pdf") || msg.contains("malformed") || msg.contains("header")) {
            return new ClassifiedError(IngestionErrorCode.MALFORMED_PDF, IngestionErrorCode.MALFORMED_PDF.getDescription(), ErrorClassification.PERMANENT);
        }

        if (throwable instanceof IOException) {
            return new ClassifiedError(IngestionErrorCode.STORAGE_READ_ERROR, IngestionErrorCode.STORAGE_READ_ERROR.getDescription(), ErrorClassification.TRANSIENT);
        }

        // Default fallback with sanitized safe message (no internal stack traces exposed)
        return new ClassifiedError(IngestionErrorCode.UNKNOWN_ERROR, "An internal error occurred during pipeline execution", ErrorClassification.TRANSIENT);
    }
}
