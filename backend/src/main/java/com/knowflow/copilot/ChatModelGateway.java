package com.knowflow.copilot;

public interface ChatModelGateway {

    record ChatResponse(
            String content,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {}

    ChatResponse generate(String systemPrompt, String userMessage);

    String getModelName();
}
