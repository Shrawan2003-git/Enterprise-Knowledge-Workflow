package com.knowflow.copilot;

public record RetrievalOptions(
    int topK,
    int maxTokens,
    double minScoreThreshold
) {
    public static RetrievalOptions defaults() {
        return new RetrievalOptions(10, 4096, 0.0);
    }
}
