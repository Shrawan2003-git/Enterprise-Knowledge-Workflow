package com.knowflow.approvals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalRequestDto(
        UUID id,
        UUID documentVersionId,
        UUID submittedBy,
        UUID approverId,
        String status,
        Instant submittedAt,
        Instant completedAt,
        int version,
        List<ApprovalHistoryDto> history
) {
    public static ApprovalRequestDto fromEntity(ApprovalRequest req, List<ApprovalHistoryDto> history) {
        return new ApprovalRequestDto(
                req.getId(),
                req.getDocumentVersionId(),
                req.getSubmittedBy(),
                req.getApproverId(),
                req.getStatus(),
                req.getSubmittedAt(),
                req.getCompletedAt(),
                req.getVersion(),
                history
        );
    }
}
