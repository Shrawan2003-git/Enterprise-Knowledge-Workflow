package com.knowflow.copilot;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
        @NotNull(message = "Rating is required")
        Integer rating,

        FeedbackReasonCode reasonCode,

        @Size(max = 1000, message = "Comment must not exceed 1000 characters")
        String comment
) {}
