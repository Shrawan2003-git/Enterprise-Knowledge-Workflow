package com.knowflow.copilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDetailDto(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<MessageDto> messages
) {}
