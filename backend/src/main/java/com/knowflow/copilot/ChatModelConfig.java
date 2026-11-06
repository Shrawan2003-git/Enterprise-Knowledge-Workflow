package com.knowflow.copilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ChatModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatModelConfig.class);

    @Value("${knowflow.ai.enabled:false}")
    private boolean aiEnabled;

    @Value("${knowflow.ai.provider:fake}")
    private String provider;

    @Value("${knowflow.ai.chat.model:gpt-4o-mini}")
    private String chatModel;

    @Value("${knowflow.ai.chat.temperature:0.0}")
    private double temperature;

    @Value("${knowflow.ai.chat.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${AI_API_KEY:${OPENAI_API_KEY:}}")
    private String apiKey;

    @Value("${AI_BASE_URL:}")
    private String baseUrl;

    @Bean
    public ChatModelGateway chatModelGateway() {
        if (!aiEnabled || "fake".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            log.info("Chat model provider configured as FAKE or AI disabled (enabled={}). Using FakeChatModel.", aiEnabled);
            return new FakeChatModel();
        }

        log.info("Configuring OpenAiChatModelGateway with model={} and timeout={}s", chatModel, timeoutSeconds);
        return new OpenAiChatModelGateway(
                baseUrl,
                apiKey,
                chatModel,
                temperature,
                Duration.ofSeconds(timeoutSeconds)
        );
    }
}
