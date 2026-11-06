package com.knowflow.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank String title,
        String description,
        @NotNull UUID assigneeId,
        UUID linkedDocumentId,
        UUID linkedApprovalId,
        String priority,
        Instant dueAt
) {}
