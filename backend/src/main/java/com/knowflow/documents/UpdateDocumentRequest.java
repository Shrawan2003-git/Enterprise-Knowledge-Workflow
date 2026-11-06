package com.knowflow.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UpdateDocumentRequest(
        @NotBlank(message = "Document title is required")
        @Size(max = 255)
        String title,

        @Size(max = 2000)
        String description,

        UUID categoryId,
        String classification,
        Set<String> tags,
        Instant expiryAt,

        @NotNull(message = "Optimistic locking version is required")
        Integer version
) {}
