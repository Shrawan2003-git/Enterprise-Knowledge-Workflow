package com.knowflow.tools;

import com.knowflow.approvals.ApprovalRequest;
import com.knowflow.approvals.ApprovalRequestRepository;
import com.knowflow.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class GetMyPendingApprovalsTool implements ToolDefinition {

    private final ApprovalRequestRepository approvalRepository;

    public GetMyPendingApprovalsTool(ApprovalRequestRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    @Override
    public String getName() {
        return "get_my_pending_approvals";
    }

    @Override
    public String getDescription() {
        return "Retrieves pending review/approval requests assigned to the current authenticated user.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "description", "Maximum items to return (1-20)")
                )
        );
    }

    public record ApprovalSummaryItem(
            UUID approvalId,
            UUID documentVersionId,
            String status,
            Instant createdAt
    ) {}

    @Override
    public ToolExecutionResult execute(Map<String, Object> rawArgs, CurrentUser user) {
        if (user == null) {
            return ToolExecutionResult.failure("User authentication is required");
        }

        // Rule: Never trust model-supplied userId — always derive strictly from CurrentUser!
        UUID actorId = user.id();

        int limit = 10;
        if (rawArgs != null && rawArgs.containsKey("limit")) {
            try {
                int parsed = Integer.parseInt(rawArgs.get("limit").toString());
                limit = Math.clamp(parsed, 1, 20);
            } catch (Exception ignored) {}
        }

        List<ApprovalRequest> requests = approvalRepository.findByApproverId(actorId, PageRequest.of(0, limit)).getContent();
        List<ApprovalSummaryItem> items = requests.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .map(r -> new ApprovalSummaryItem(r.getId(), r.getDocumentVersionId(), r.getStatus(), r.getSubmittedAt()))
                .toList();

        return ToolExecutionResult.success(items);
    }
}
