package com.knowflow.ingestion;

public record PageExtractionResult(
        int pageNumber,
        String text
) {}
