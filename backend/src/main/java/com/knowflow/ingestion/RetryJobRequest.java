package com.knowflow.ingestion;

import jakarta.validation.constraints.NotBlank;

public record RetryJobRequest(
        @NotBlank(message = "Reason is mandatory for audit trail")
        String reason
) {}
