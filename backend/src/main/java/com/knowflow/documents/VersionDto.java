package com.knowflow.documents;

import java.time.Instant;
import java.util.UUID;

public record VersionDto(
        UUID id,
        UUID documentId,
        int versionNumber,
        String checksum,
        String mimeType,
        long sizeBytes,
        UUID uploadedBy,
        String changeNote,
        String processingStatus,
        String errorCode,
        String activePipelineVersion,
        Instant createdAt
) {
    public static VersionDto fromEntity(DocumentVersion v) {
        return new VersionDto(
                v.getId(),
                v.getDocumentId(),
                v.getVersionNumber(),
                v.getChecksum(),
                v.getMimeType(),
                v.getSizeBytes(),
                v.getUploadedBy(),
                v.getChangeNote(),
                v.getProcessingStatus(),
                v.getErrorCode(),
                v.getActivePipelineVersion(),
                v.getCreatedAt()
        );
    }
}
