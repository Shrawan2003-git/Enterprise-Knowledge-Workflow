package com.knowflow.copilot;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryDto(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        int messageCount
) {}
