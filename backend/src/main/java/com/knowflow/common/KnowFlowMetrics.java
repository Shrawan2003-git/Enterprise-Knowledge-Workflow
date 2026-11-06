package com.knowflow.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central Micrometer metrics collector for KnowFlow operations,
 * RAG latency, AI token accounting, ingestion backlog, and error rates.
 */
@Component
public class KnowFlowMetrics {

    private final MeterRegistry registry;

    private final Timer retrievalTimer;
    private final Timer generationTimer;
    private final Counter tokenUsageCounter;
    private final Counter timeoutCounter;
    private final Counter rateLimitCounter;
    private final Counter embeddingThroughputCounter;
    private final Counter ingestionFailureCounter;
    private final AtomicInteger pendingQueueDepth;

    public KnowFlowMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.retrievalTimer = Timer.builder("knowflow.copilot.retrieval.duration")
                .description("Time taken to execute hybrid retrieval and SQL-level ACL filtering")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.generationTimer = Timer.builder("knowflow.copilot.generation.duration")
                .description("Time taken for LLM chat completion generation")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.tokenUsageCounter = Counter.builder("knowflow.copilot.tokens.total")
                .description("Total LLM prompt and completion tokens consumed")
                .register(registry);

        this.timeoutCounter = Counter.builder("knowflow.copilot.timeouts.total")
                .description("Count of AI provider timeouts or network connection errors")
                .register(registry);

        this.rateLimitCounter = Counter.builder("knowflow.copilot.ratelimits.total")
                .description("Count of requests hitting per-user or provider rate limits")
                .register(registry);

        this.embeddingThroughputCounter = Counter.builder("knowflow.ingestion.embeddings.total")
                .description("Total document chunks embedded via EmbeddingService")
                .register(registry);

        this.ingestionFailureCounter = Counter.builder("knowflow.ingestion.failures.total")
                .description("Total failed document ingestion or processing jobs")
                .register(registry);

        this.pendingQueueDepth = new AtomicInteger(0);
        registry.gauge("knowflow.ingestion.queue.depth", pendingQueueDepth);
    }

    public void recordRetrievalDuration(Duration duration) {
        retrievalTimer.record(duration);
    }

    public void recordGenerationDuration(Duration duration) {
        generationTimer.record(duration);
    }

    public void recordTokenUsage(int tokens) {
        if (tokens > 0) {
            tokenUsageCounter.increment(tokens);
        }
    }

    public void recordTimeout() {
        timeoutCounter.increment();
    }

    public void recordRateLimit() {
        rateLimitCounter.increment();
    }

    public void recordChunksEmbedded(int count) {
        if (count > 0) {
            embeddingThroughputCounter.increment(count);
        }
    }

    public void recordIngestionFailure() {
        ingestionFailureCounter.increment();
    }

    public void setPendingQueueDepth(int depth) {
        pendingQueueDepth.set(Math.max(0, depth));
    }
}
