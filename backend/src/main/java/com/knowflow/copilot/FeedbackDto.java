package com.knowflow.copilot;

import java.time.Instant;
import java.util.UUID;

public record FeedbackDto(
        UUID id,
        int rating,
        FeedbackReasonCode reasonCode,
        String comment,
        Instant createdAt
) {}
