package com.knowflow.copilot;

import java.util.UUID;

public record CitationDto(
        UUID id,
        String sourceId,
        UUID documentId,
        String documentTitle,
        int versionNumber,
        Integer pageStart,
        Integer pageEnd,
        String pageHint,
        String snippet,
        boolean canAccess
) {}
