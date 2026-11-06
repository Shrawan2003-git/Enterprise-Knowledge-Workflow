package com.knowflow.ingestion;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Component
@ConditionalOnProperty(name = "knowflow.ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);

    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private final JobDispatcher jobDispatcher;
    private final IngestionPipelineRunner pipelineRunner;
    private final ErrorClassifier errorClassifier;
    private final IngestionMetrics metrics;

    @Value("${knowflow.ingestion.concurrency:2}")
    private int concurrency;

    @Value("${knowflow.ingestion.lease-duration-seconds:60}")
    private int leaseSeconds;

    private ExecutorService threadPool;
    private ScheduledExecutorService heartbeatExecutor;

    public IngestionWorker(
            JobDispatcher jobDispatcher,
            IngestionPipelineRunner pipelineRunner,
            ErrorClassifier errorClassifier,
            IngestionMetrics metrics
    ) {
        this.jobDispatcher = jobDispatcher;
        this.pipelineRunner = pipelineRunner;
        this.errorClassifier = errorClassifier;
        this.metrics = metrics;
    }

    @PostConstruct
    public void start() {
        this.threadPool = Executors.newFixedThreadPool(Math.max(1, concurrency));
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        log.info("IngestionWorker [{}] initialized with concurrency={}", workerId, concurrency);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down IngestionWorker [{}]", workerId);
        if (threadPool != null) {
            threadPool.shutdown();
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
        }
    }

    @Scheduled(fixedDelayString = "${knowflow.ingestion.poll-interval-ms:3000}")
    public void pollAndProcess() {
        try {
            Duration leaseDuration = Duration.ofSeconds(leaseSeconds);
            List<IngestionJob> claimed = jobDispatcher.claimBatch(workerId, leaseDuration, concurrency);

            for (IngestionJob job : claimed) {
                threadPool.submit(() -> processJobWithHeartbeat(job, leaseDuration));
            }
        } catch (Exception e) {
            log.error("Error during job poll cycle: {}", e.getMessage(), e);
        }
    }

    private void processJobWithHeartbeat(IngestionJob job, Duration leaseDuration) {
        Instant startTime = Instant.now();
        ScheduledFuture<?> heartbeatTask = null;

        try {
            // Heartbeat every 1/3 of the lease duration
            long heartbeatIntervalMs = Math.max(1000, leaseDuration.toMillis() / 3);
            heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
                try {
                    jobDispatcher.heartbeat(job.getId(), workerId, leaseDuration);
                } catch (Exception e) {
                    log.warn("Heartbeat failed for job id={}: {}", job.getId(), e.getMessage());
                }
            }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);

            pipelineRunner.execute(job);

            jobDispatcher.completeJob(job.getId());
            metrics.recordJobDuration(Duration.between(startTime, Instant.now()), true);

        } catch (Throwable t) {
            Duration duration = Duration.between(startTime, Instant.now());
            metrics.recordJobDuration(duration, false);

            ErrorClassifier.ClassifiedError classified = errorClassifier.classify(t);
            log.error("Pipeline failure on job id={}, classified as {} ({}): {}",
                    job.getId(), classified.code(), classified.classification(), classified.safeMessage());

            if (classified.classification() == ErrorClassification.PERMANENT) {
                jobDispatcher.failJob(job.getId(), classified.code(), classified.safeMessage());
            } else {
                jobDispatcher.scheduleRetry(job.getId(), classified.code(), classified.safeMessage(), Duration.ofSeconds(5));
            }
        } finally {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
        }
    }

    public String getWorkerId() {
        return workerId;
    }
}
