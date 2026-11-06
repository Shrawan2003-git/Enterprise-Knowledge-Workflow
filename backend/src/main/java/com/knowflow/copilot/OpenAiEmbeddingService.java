package com.knowflow.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int dimensions;
    private final int maxRetries = 3;

    public OpenAiEmbeddingService(String apiKey, String baseUrl, String model, int dimensions) {
        this.model = model != null ? model : "text-embedding-3-small";
        this.dimensions = dimensions > 0 ? dimensions : 1536;
        this.objectMapper = new ObjectMapper();
        
        String resolvedBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "https://api.openai.com/v1";
        this.restClient = RestClient.builder()
                .baseUrl(resolvedBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> results = embedBatch(List.of(text));
        if (results.isEmpty()) {
            throw new RuntimeException("Embedding API returned empty results");
        }
        return results.getFirst();
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<String, Object> body = Map.of(
                "input", texts,
                "model", model,
                "dimensions", dimensions
        );

        int attempt = 0;
        long backoffMs = 500;

        while (true) {
            try {
                attempt++;
                String responseBody = restClient.post()
                        .uri("/embeddings")
                        .body(body)
                        .retrieve()
                        .body(String.class);

                return parseEmbeddingResponse(responseBody, texts.size());
            } catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException e) {
                if (attempt >= maxRetries) {
                    log.error("Embedding API failed with {} after {} attempts: {}", e.getStatusCode(), attempt, e.getMessage());
                    throw new RuntimeException("Embedding provider rate-limited or unavailable: " + e.getMessage(), e);
                }
                log.warn("Embedding API returned {}, retrying in {} ms (attempt {}/{})", e.getStatusCode(), backoffMs, attempt, maxRetries);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Embedding interrupted", ie);
                }
                backoffMs *= 2;
            } catch (Exception e) {
                log.error("Embedding API invocation failed: {}", e.getMessage(), e);
                throw new RuntimeException("Embedding API invocation failed: " + e.getMessage(), e);
            }
        }
    }

    private List<List<Float>> parseEmbeddingResponse(String json, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataNode = root.path("data");
            List<List<Float>> vectors = new ArrayList<>(expectedCount);

            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    JsonNode embNode = item.path("embedding");
                    if (embNode.isArray()) {
                        List<Float> vector = new ArrayList<>(embNode.size());
                        for (JsonNode val : embNode) {
                            vector.add((float) val.asDouble());
                        }
                        if (vector.size() != dimensions) {
                            throw new IllegalStateException("Dimension mismatch: expected " + dimensions + " but got " + vector.size());
                        }
                        vectors.add(vector);
                    }
                }
            }
            return vectors;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse embedding response: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }
}
