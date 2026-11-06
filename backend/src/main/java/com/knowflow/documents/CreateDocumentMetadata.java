package com.knowflow.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CreateDocumentMetadata(
        @NotBlank(message = "Document title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        UUID departmentId,
        UUID categoryId,
        String classification,
        Set<String> tags,
        Instant expiryAt,
        String changeNote
) {}
