package com.knowflow.ingestion;

public enum IngestionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    RETRY_SCHEDULED
}
