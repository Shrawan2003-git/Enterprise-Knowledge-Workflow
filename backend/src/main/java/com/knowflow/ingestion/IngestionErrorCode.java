package com.knowflow.ingestion;

public enum IngestionErrorCode {
    PDF_ENCRYPTED("Document is password protected or encrypted", ErrorClassification.PERMANENT),
    MALFORMED_PDF("File cannot be parsed as a valid PDF", ErrorClassification.PERMANENT),
    FILE_NOT_FOUND("Physical document file could not be located in storage", ErrorClassification.PERMANENT),
    PAGE_LIMIT_EXCEEDED("Document exceeds maximum allowed page count", ErrorClassification.PERMANENT),
    SCAN_FAILED("Document failed security/antivirus inspection", ErrorClassification.PERMANENT),
    PARSER_TIMEOUT("Parsing operation exceeded maximum allocated time", ErrorClassification.TRANSIENT),
    STORAGE_READ_ERROR("Transient I/O failure reading from file storage", ErrorClassification.TRANSIENT),
    DATABASE_ERROR("Transient database communication error", ErrorClassification.TRANSIENT),
    UNKNOWN_ERROR("Unexpected internal error during ingestion", ErrorClassification.TRANSIENT);

    private final String description;
    private final ErrorClassification defaultClassification;

    IngestionErrorCode(String description, ErrorClassification defaultClassification) {
        this.description = description;
        this.defaultClassification = defaultClassification;
    }

    public String getDescription() {
        return description;
    }

    public ErrorClassification getDefaultClassification() {
        return defaultClassification;
    }
}
