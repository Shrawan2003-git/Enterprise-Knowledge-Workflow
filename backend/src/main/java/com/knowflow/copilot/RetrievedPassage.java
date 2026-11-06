package com.knowflow.copilot;

import java.util.UUID;

public record RetrievedPassage(
    UUID chunkId,
    UUID versionId,
    UUID documentId,
    String documentTitle,
    int versionNumber,
    int pageStart,
    int pageEnd,
    String text,
    double score,
    int tokenCount
) {}
