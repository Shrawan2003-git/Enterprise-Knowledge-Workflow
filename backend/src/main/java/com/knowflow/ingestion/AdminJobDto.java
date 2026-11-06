package com.knowflow.ingestion;

import java.time.Instant;
import java.util.UUID;

public record AdminJobDto(
        UUID id,
        UUID versionId,
        String pipelineVersion,
        String status,
        int attempts,
        int maxAttempts,
        Instant nextAttemptAt,
        Instant startedAt,
        Instant finishedAt,
        String errorCode,
        String errorMessage,
        String errorType,
        String lockedBy,
        Instant lockedUntil,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminJobDto fromEntity(IngestionJob job) {
        return new AdminJobDto(
                job.getId(),
                job.getVersionId(),
                job.getPipelineVersion(),
                job.getStatus(),
                job.getAttempts(),
                job.getMaxAttempts(),
                job.getNextAttemptAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getErrorType(),
                job.getLockedBy(),
                job.getLockedUntil(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
