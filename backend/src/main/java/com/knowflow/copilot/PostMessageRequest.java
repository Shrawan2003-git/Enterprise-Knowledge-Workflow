package com.knowflow.copilot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        @Size(max = 2000, message = "Message content must not exceed 2000 characters")
        String content
) {}
