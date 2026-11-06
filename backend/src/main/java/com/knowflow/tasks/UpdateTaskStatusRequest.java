package com.knowflow.tasks;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(
        @NotBlank String status,
        String completionNote,
        @NotNull Integer version
) {}
