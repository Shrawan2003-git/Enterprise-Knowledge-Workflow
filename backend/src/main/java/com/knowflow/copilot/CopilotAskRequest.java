package com.knowflow.copilot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CopilotAskRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 1000, message = "Question must not exceed 1000 characters")
        String question,
        Integer topK,
        Double minScore
) {
}
