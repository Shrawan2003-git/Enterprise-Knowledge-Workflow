package com.knowflow.copilot;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Value("${knowflow.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${knowflow.ai.provider:fake}")
    private String provider;

    @Value("${knowflow.ai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${knowflow.ai.embedding.dimensions:1536}")
    private int dimensions;

    @Value("${AI_API_KEY:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${AI_BASE_URL:}")
    private String baseUrl;

    @Bean
    public EmbeddingService embeddingService() {
        if (!aiEnabled || "fake".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            log.info("AI provider configured as FAKE or AI disabled (enabled={}). Using FakeEmbeddingService (dim={}).", aiEnabled, dimensions);
            return new FakeEmbeddingService(dimensions);
        }

        log.info("Configuring OpenAiEmbeddingService with model={} and dimensions={}", embeddingModel, dimensions);
        return new OpenAiEmbeddingService(apiKey, baseUrl, embeddingModel, dimensions);
    }

    @PostConstruct
    public void validateOnStartup() {
        if (!aiEnabled) {
            log.info("KnowFlow AI features are disabled (knowflow.ai.enabled=false). App booting in keyword-only mode.");
            return;
        }

        log.info("Validating EmbeddingService configuration on startup (target dimensions={})...", dimensions);
        EmbeddingService service = embeddingService();
        List<Float> testVector = service.embed("Startup validation probe for KnowFlow embedding service.");
        if (testVector == null || testVector.size() != dimensions) {
            throw new IllegalStateException("Startup embedding validation failed: expected dimension " + dimensions
                    + " but received " + (testVector == null ? 0 : testVector.size()));
        }
        log.info("EmbeddingService startup dimension validation passed ({} dimensions).", dimensions);
    }
}
