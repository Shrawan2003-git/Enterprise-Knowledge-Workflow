package com.knowflow.ingestion;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ErrorClassifierTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    void classifiesPermanentErrorsCorrectly() {
        var encryptedErr = classifier.classify(new RuntimeException("PDF is password encrypted"));
        assertEquals(IngestionErrorCode.PDF_ENCRYPTED, encryptedErr.code());
        assertEquals(ErrorClassification.PERMANENT, encryptedErr.classification());

        var malformedErr = classifier.classify(new RuntimeException("malformed PDF header missing"));
        assertEquals(IngestionErrorCode.MALFORMED_PDF, malformedErr.code());
        assertEquals(ErrorClassification.PERMANENT, malformedErr.classification());

        var notFoundErr = classifier.classify(new FileNotFoundException("file not found"));
        assertEquals(IngestionErrorCode.FILE_NOT_FOUND, notFoundErr.code());
        assertEquals(ErrorClassification.PERMANENT, notFoundErr.classification());
    }

    @Test
    void classifiesTransientErrorsCorrectly() {
        var timeoutErr = classifier.classify(new TimeoutException("Operation timed out"));
        assertEquals(IngestionErrorCode.PARSER_TIMEOUT, timeoutErr.code());
        assertEquals(ErrorClassification.TRANSIENT, timeoutErr.classification());

        var ioErr = classifier.classify(new IOException("Disk read failed"));
        assertEquals(IngestionErrorCode.STORAGE_READ_ERROR, ioErr.code());
        assertEquals(ErrorClassification.TRANSIENT, ioErr.classification());
    }

    @Test
    void masksSensitiveInternalDetailsFromSafeMessage() {
        var unknown = classifier.classify(new RuntimeException("Internal SQL Connection Refused at ip 10.0.0.1:5432"));
        assertEquals(IngestionErrorCode.UNKNOWN_ERROR, unknown.code());
        assertFalse(unknown.safeMessage().contains("10.0.0.1"));
        assertFalse(unknown.safeMessage().contains("SQL"));
    }
}
