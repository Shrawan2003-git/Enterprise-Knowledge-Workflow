package com.knowflow.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knowflow.common.KnowFlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Spring RestClient implementation of ChatModelGateway connecting to OpenAI-compatible chat completions API.
 */
public class OpenAiChatModelGateway implements ChatModelGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatModelGateway.class);

    private final RestClient restClient;
    private final String model;
    private final double temperature;

    public OpenAiChatModelGateway(String baseUrl, String apiKey, String model, double temperature, Duration timeout) {
        this.model = model != null && !model.isBlank() ? model : "gpt-4o-mini";
        this.temperature = temperature;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (apiKey != null ? apiKey : ""))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    record ChatMessage(String role, String content) {}

    record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            @JsonProperty("response_format") Map<String, String> responseFormat
    ) {}

    record ChatChoice(ChatMessage message) {}
    record ChatUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {}

    record ChatCompletionResponse(
            List<ChatChoice> choices,
            ChatUsage usage
    ) {}

    @Override
    public ChatResponse generate(String systemPrompt, String userMessage) {
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userMessage)
        );

        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                temperature,
                Map.of("type", "json_object")
        );

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new KnowFlowException("AI_EMPTY_RESPONSE", "Received empty response from chat completions provider", 502);
            }

            String content = response.choices().getFirst().message().content();
            Integer promptTokens = response.usage() != null ? response.usage().promptTokens() : null;
            Integer completionTokens = response.usage() != null ? response.usage().completionTokens() : null;
            Integer totalTokens = response.usage() != null ? response.usage().totalTokens() : null;

            return new ChatResponse(content, promptTokens, completionTokens, totalTokens);
        } catch (RestClientResponseException e) {
            log.error("Chat completion API failed with status={}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new KnowFlowException("AI_RATE_LIMIT", "AI model provider rate limit exceeded", 429);
            }
            throw new KnowFlowException("AI_PROVIDER_ERROR", "AI model provider error: " + e.getMessage(), 502);
        } catch (Exception e) {
            log.error("Chat completion API call failed: {}", e.getMessage());
            throw new KnowFlowException("AI_TIMEOUT", "AI model provider request timed out or network error", 504);
        }
    }

    @Override
    public String getModelName() {
        return model;
    }
}
