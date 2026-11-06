package com.knowflow.approvals;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitReviewRequest(
        @NotNull UUID versionId,
        @NotNull UUID approverId
) {}
