package com.knowflow.copilot;

import java.util.List;

public record CopilotAnswer(
        String answer,
        List<CopilotCitation> citations,
        ConfidenceLevel confidence,
        String model,
        String promptVersion,
        long retrievalMs,
        long generationMs,
        Integer tokenUsage,
        boolean isRecoverableError,
        String disclaimer
) {
    public static final String STANDARD_DISCLAIMER =
            "Answers are grounded strictly in retrieved authorized documents. AI can make mistakes — verify critical information via cited sources.";

    public static CopilotAnswer insufficient(String reason, long retrievalMs) {
        return new CopilotAnswer(
                reason != null ? reason : "I could not find sufficient information in authorized documents to answer this question.",
                List.of(),
                ConfidenceLevel.INSUFFICIENT,
                "none",
                PromptTemplate.PROMPT_VERSION,
                retrievalMs,
                0,
                0,
                false,
                STANDARD_DISCLAIMER
        );
    }

    public static CopilotAnswer conflict(String explanation, List<CopilotCitation> conflictingCitations, long retrievalMs, long generationMs, String model) {
        return new CopilotAnswer(
                explanation,
                conflictingCitations != null ? conflictingCitations : List.of(),
                ConfidenceLevel.CONFLICT,
                model != null ? model : "none",
                PromptTemplate.PROMPT_VERSION,
                retrievalMs,
                generationMs,
                null,
                false,
                STANDARD_DISCLAIMER
        );
    }

    public static CopilotAnswer recoverableError(String message) {
        return new CopilotAnswer(
                message != null ? message : "The AI generation provider timed out or is temporarily overloaded. Your question is preserved; please retry in a moment.",
                List.of(),
                ConfidenceLevel.INSUFFICIENT,
                "none",
                PromptTemplate.PROMPT_VERSION,
                0,
                0,
                0,
                true,
                STANDARD_DISCLAIMER
        );
    }
}
