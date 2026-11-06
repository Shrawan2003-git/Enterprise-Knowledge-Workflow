package com.knowflow.ingestion;

import jakarta.validation.constraints.NotBlank;

public record ReprocessRequest(
        @NotBlank(message = "Pipeline version is required (e.g. v2)")
        String pipelineVersion,

        @NotBlank(message = "Reason is mandatory for audit trail")
        String reason
) {}
