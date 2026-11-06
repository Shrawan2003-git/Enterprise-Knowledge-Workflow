package com.knowflow.documents;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record DocumentSummaryDto(
        UUID id,
        String title,
        String description,
        String status,
        String classification,
        String categoryName,
        Set<String> tags,
        UUID ownerId,
        UUID departmentId,
        Integer currentVersionNumber,
        Instant expiryAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentSummaryDto fromEntity(Document doc) {
        Set<String> tagNames = doc.getTags() != null ?
                doc.getTags().stream().map(Tag::getName).collect(Collectors.toSet()) : Set.of();

        return new DocumentSummaryDto(
                doc.getId(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getStatus(),
                doc.getClassification(),
                doc.getCategory() != null ? doc.getCategory().getName() : null,
                tagNames,
                doc.getOwnerId(),
                doc.getDepartmentId(),
                doc.getCurrentVersion() != null ? doc.getCurrentVersion().getVersionNumber() : null,
                doc.getExpiryAt(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
