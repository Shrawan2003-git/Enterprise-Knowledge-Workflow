package com.knowflow.tasks;

import java.time.Instant;
import java.util.UUID;

public record TaskDto(
        UUID id,
        String title,
        String description,
        UUID assigneeId,
        UUID creatorId,
        UUID linkedDocumentId,
        UUID linkedApprovalId,
        String priority,
        String status,
        Instant dueAt,
        String completionNote,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static TaskDto fromEntity(Task t) {
        return new TaskDto(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getAssigneeId(),
                t.getCreatorId(),
                t.getLinkedDocumentId(),
                t.getLinkedApprovalId(),
                t.getPriority(),
                t.getStatus(),
                t.getDueAt(),
                t.getCompletionNote(),
                t.getVersion(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
