package com.knowflow.ingestion;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IngestionMetrics {

    private final MeterRegistry registry;
    private final Counter retryCounter;

    public IngestionMetrics(MeterRegistry registry, IngestionJobRepository repository) {
        this.registry = registry;
        this.retryCounter = Counter.builder("knowflow.ingestion.retries")
                .description("Total number of ingestion retry attempts scheduled")
                .register(registry);

        registry.gauge("knowflow.ingestion.queue.depth", repository, IngestionJobRepository::countActiveQueue);
    }

    public void recordJobDuration(Duration duration, boolean success) {
        Timer.builder("knowflow.ingestion.duration")
                .tag("success", Boolean.toString(success))
                .description("Execution duration of document ingestion jobs")
                .register(registry)
                .record(duration);
    }

    public void recordFailure(String errorCode) {
        Counter.builder("knowflow.ingestion.failures")
                .tag("code", errorCode != null ? errorCode : "UNKNOWN")
                .description("Total number of failed ingestion jobs")
                .register(registry)
                .increment();
    }

    public void recordRetry() {
        retryCounter.increment();
    }
}
