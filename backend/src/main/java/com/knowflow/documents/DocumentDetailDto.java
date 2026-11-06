package com.knowflow.documents;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record DocumentDetailDto(
        UUID id,
        String title,
        String description,
        String status,
        String classification,
        String categoryName,
        Set<String> tags,
        UUID ownerId,
        UUID departmentId,
        VersionDto currentVersion,
        Instant expiryAt,
        int version,
        Instant createdAt,
        Instant updatedAt,
        List<DocumentPermissionDto> permissions
) {
    public static DocumentDetailDto fromEntity(Document doc, List<DocumentPermissionDto> permissions) {
        Set<String> tagNames = doc.getTags() != null ?
                doc.getTags().stream().map(Tag::getName).collect(Collectors.toSet()) : Set.of();

        return new DocumentDetailDto(
                doc.getId(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getStatus(),
                doc.getClassification(),
                doc.getCategory() != null ? doc.getCategory().getName() : null,
                tagNames,
                doc.getOwnerId(),
                doc.getDepartmentId(),
                doc.getCurrentVersion() != null ? VersionDto.fromEntity(doc.getCurrentVersion()) : null,
                doc.getExpiryAt(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                permissions
        );
    }
}
