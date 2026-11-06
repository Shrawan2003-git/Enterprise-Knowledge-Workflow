package com.knowflow.copilot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        String model,
        String promptVersion,
        String confidence,
        Instant createdAt,
        List<CitationDto> citations,
        FeedbackDto feedback
) {}
