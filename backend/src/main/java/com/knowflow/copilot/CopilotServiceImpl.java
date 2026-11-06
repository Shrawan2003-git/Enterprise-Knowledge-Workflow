package com.knowflow.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.KnowFlowException;
import com.knowflow.common.KnowFlowMetrics;
import com.knowflow.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CopilotServiceImpl implements CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotServiceImpl.class);

    private final RetrievalService retrievalService;
    private final ChatModelGateway chatModelGateway;
    private final ObjectMapper objectMapper;
    private final boolean aiEnabled;
    private final int rateLimitPerHour;

    // Sliding-window timestamps per userId
    private final Map<UUID, List<Long>> userRequestTimestamps = new ConcurrentHashMap<>();

    private final KnowFlowMetrics metrics;

    public CopilotServiceImpl(
            RetrievalService retrievalService,
            ChatModelGateway chatModelGateway,
            ObjectMapper objectMapper,
            boolean aiEnabled,
            int rateLimitPerHour
    ) {
        this(retrievalService, chatModelGateway, objectMapper, null, aiEnabled, rateLimitPerHour);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CopilotServiceImpl(
            RetrievalService retrievalService,
            ChatModelGateway chatModelGateway,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<KnowFlowMetrics> metricsProvider,
            @Value("${knowflow.ai.enabled:false}") boolean aiEnabled,
            @Value("${knowflow.copilot.rate-limit-per-hour:30}") int rateLimitPerHour
    ) {
        this.retrievalService = retrievalService;
        this.chatModelGateway = chatModelGateway;
        this.objectMapper = objectMapper;
        this.metrics = metricsProvider != null ? metricsProvider.getIfAvailable() : null;
        this.aiEnabled = aiEnabled;
        this.rateLimitPerHour = rateLimitPerHour;
    }

    @Override
    public CopilotAnswer ask(String question, CurrentUser user, RetrievalOptions options) {
        if (user == null) {
            throw new KnowFlowException("UNAUTHORIZED", "User must be authenticated", 401);
        }
        if (question == null || question.isBlank()) {
            throw new KnowFlowException("INVALID_QUERY", "Question must not be empty", 400);
        }

        // 1. Per-user rate limiting check (30 requests/hour)
        checkRateLimit(user.id());

        RetrievalOptions opts = options != null ? options : RetrievalOptions.defaults();

        // 2. Retrieval step
        long retrievalStart = System.currentTimeMillis();
        List<RetrievedPassage> passages = retrievalService.retrieve(question, user, opts);
        long retrievalMs = System.currentTimeMillis() - retrievalStart;
        if (metrics != null) {
            metrics.recordRetrievalDuration(java.time.Duration.ofMillis(retrievalMs));
        }

        // 3. Abstention: No passages retrieved or below threshold
        if (passages.isEmpty()) {
            log.info("Copilot abstaining: 0 passages retrieved for query='{}', user={}", question, user.id());
            return CopilotAnswer.insufficient(
                    "I could not find sufficient information in authorized documents to answer this question.",
                    retrievalMs
            );
        }

        // Check if top score meets minimum threshold
        double maxScore = passages.stream().mapToDouble(RetrievedPassage::score).max().orElse(0.0);
        if (maxScore < opts.minScoreThreshold()) {
            log.info("Copilot abstaining: max passage score {} < minScoreThreshold {}", maxScore, opts.minScoreThreshold());
            return CopilotAnswer.insufficient(
                    "The retrieved documents have insufficient relevance to confidently answer your question.",
                    retrievalMs
            );
        }

        // 4. Prompt Construction
        String systemPrompt = PromptTemplate.SYSTEM_PROMPT;
        String userPrompt = PromptTemplate.buildUserPrompt(question, passages);

        // 5. LLM Invocation
        long genStart = System.currentTimeMillis();
        ChatModelGateway.ChatResponse chatResponse;
        try {
            chatResponse = chatModelGateway.generate(systemPrompt, userPrompt);
        } catch (KnowFlowException e) {
            if (metrics != null) {
                if (e.getStatusCode() == 429) metrics.recordRateLimit();
                else metrics.recordTimeout();
            }
            if (e.getStatusCode() == 429 || e.getStatusCode() == 504 || e.getStatusCode() == 502) {
                log.warn("AI generation provider recoverable error (status={}): {}", e.getStatusCode(), e.getMessage());
                return CopilotAnswer.recoverableError(e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            if (metrics != null) {
                metrics.recordTimeout();
            }
            log.error("AI generation failed unexpectedly: {}", e.getMessage(), e);
            return CopilotAnswer.recoverableError("The AI generation provider timed out. Your question is preserved; please retry.");
        }
        long genMs = System.currentTimeMillis() - genStart;
        if (metrics != null) {
            metrics.recordGenerationDuration(java.time.Duration.ofMillis(genMs));
            if (chatResponse.totalTokens() != null) {
                metrics.recordTokenUsage(chatResponse.totalTokens());
            }
        }

        // 6. Parse structured response
        ParsedLlmOutput parsed = parseOutput(chatResponse.content());

        // 7. Citation Validation & Grounding Enforcement
        CitationValidator.ValidationResult validated = CitationValidator.validate(
                parsed.answer(),
                parsed.citations(),
                parsed.confidence(),
                passages
        );

        log.info("Copilot answered question='{}' confidence={} citationsCount={} retrievalMs={} genMs={}",
                question, validated.confidence(), validated.validCitations().size(), retrievalMs, genMs);

        return new CopilotAnswer(
                validated.sanitizedAnswer(),
                validated.validCitations(),
                validated.confidence(),
                chatModelGateway.getModelName(),
                PromptTemplate.PROMPT_VERSION,
                retrievalMs,
                genMs,
                chatResponse.totalTokens(),
                false,
                CopilotAnswer.STANDARD_DISCLAIMER
        );
    }

    private synchronized void checkRateLimit(UUID userId) {
        long now = System.currentTimeMillis();
        long oneHourAgo = now - 3600_000L;

        List<Long> timestamps = userRequestTimestamps.computeIfAbsent(userId, k -> new ArrayList<>());
        timestamps.removeIf(t -> t < oneHourAgo);

        if (timestamps.size() >= rateLimitPerHour) {
            throw new KnowFlowException(
                    "RATE_LIMIT_EXCEEDED",
                    "Copilot rate limit exceeded (" + rateLimitPerHour + " requests/hour). Please wait before asking again.",
                    429
            );
        }

        timestamps.add(now);
    }

    record ParsedLlmOutput(
            String answer,
            List<String> citations,
            ConfidenceLevel confidence
    ) {}

    private ParsedLlmOutput parseOutput(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return new ParsedLlmOutput("No response content generated.", List.of(), ConfidenceLevel.INSUFFICIENT);
        }

        String cleaned = rawContent.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String answer = node.has("answer") ? node.get("answer").asText() : "";
            String confStr = node.has("confidence") ? node.get("confidence").asText() : "ANSWERED";

            ConfidenceLevel confidence;
            try {
                confidence = ConfidenceLevel.valueOf(confStr.toUpperCase());
            } catch (Exception e) {
                confidence = ConfidenceLevel.ANSWERED;
            }

            List<String> citationIds = new ArrayList<>();
            if (node.has("citations") && node.get("citations").isArray()) {
                for (JsonNode cNode : node.get("citations")) {
                    if (cNode.has("sourceId")) {
                        citationIds.add(cNode.get("sourceId").asText());
                    } else if (cNode.isTextual()) {
                        citationIds.add(cNode.asText());
                    }
                }
            }

            return new ParsedLlmOutput(answer, citationIds, confidence);
        } catch (Exception e) {
            log.warn("Failed to parse LLM structured JSON output. Raw content: {}", cleaned);
            return new ParsedLlmOutput(cleaned, List.of(), ConfidenceLevel.ANSWERED);
        }
    }
}
