package com.knowflow.tools;

public record ToolExecutionResult(
        boolean success,
        Object data,
        String errorMessage
) {
    public static ToolExecutionResult success(Object data) {
        return new ToolExecutionResult(true, data, null);
    }

    public static ToolExecutionResult failure(String errorMessage) {
        return new ToolExecutionResult(false, null, errorMessage);
    }
}
