package com.knowflow.approvals;

import java.time.Instant;
import java.util.UUID;

public record ApprovalHistoryDto(
        UUID id,
        UUID actorId,
        String action,
        String comment,
        String fromState,
        String toState,
        Instant createdAt
) {
    public static ApprovalHistoryDto fromEntity(ApprovalHistory h) {
        return new ApprovalHistoryDto(
                h.getId(),
                h.getActorId(),
                h.getAction(),
                h.getComment(),
                h.getFromState(),
                h.getToState(),
                h.getCreatedAt()
        );
    }
}
