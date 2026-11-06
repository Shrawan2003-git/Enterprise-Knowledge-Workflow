package com.knowflow.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class DatabaseJobDispatcher implements JobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DatabaseJobDispatcher.class);

    private final IngestionJobRepository jobRepository;
    private final IngestionMetrics metrics;

    @Value("${knowflow.ingestion.max-attempts:3}")
    private int defaultMaxAttempts;

    @Value("${knowflow.ingestion.base-backoff-ms:1000}")
    private long baseBackoffMs;

    @Value("${knowflow.ingestion.max-backoff-ms:30000}")
    private long maxBackoffMs;

    public DatabaseJobDispatcher(IngestionJobRepository jobRepository, IngestionMetrics metrics) {
        this.jobRepository = jobRepository;
        this.metrics = metrics;
    }

    @Override
    public IngestionJob enqueue(UUID versionId, String pipelineVersion) {
        String version = (pipelineVersion != null && !pipelineVersion.isBlank()) ? pipelineVersion : "v1";

        Optional<IngestionJob> existing = jobRepository.findByVersionIdAndPipelineVersion(versionId, version);
        if (existing.isPresent()) {
            log.info("Job already enqueued for versionId={} pipelineVersion={}", versionId, version);
            return existing.get();
        }

        IngestionJob job = new IngestionJob(versionId, version);
        job.setMaxAttempts(defaultMaxAttempts);
        job.setStatus(IngestionStatus.PENDING.name());
        job.setNextAttemptAt(Instant.now());

        IngestionJob saved = jobRepository.save(job);
        log.info("Enqueued new ingestion job id={} for versionId={}", saved.getId(), versionId);
        return saved;
    }

    @Override
    public List<IngestionJob> claimBatch(String workerId, Duration leaseDuration, int limit) {
        Instant now = Instant.now();
        Instant lockUntil = now.plus(leaseDuration);

        // Fetch candidate IDs in order
        List<IngestionJob> candidates = jobRepository.findClaimableCandidates(now, PageRequest.of(0, limit * 2));
        List<IngestionJob> claimed = new ArrayList<>();

        for (IngestionJob candidate : candidates) {
            if (claimed.size() >= limit) {
                break;
            }
            int updated = jobRepository.tryClaimLease(candidate.getId(), workerId, now, lockUntil);
            if (updated > 0) {
                candidate.setStatus(IngestionStatus.RUNNING.name());
                candidate.setLockedBy(workerId);
                candidate.setLockedUntil(lockUntil);
                candidate.setStartedAt(now);
                candidate.setAttempts(candidate.getAttempts() + 1);
                claimed.add(candidate);
                log.info("Worker [{}] claimed job id={} (attempt {})", workerId, candidate.getId(), candidate.getAttempts());
            }
        }

        return claimed;
    }

    @Override
    public boolean heartbeat(UUID jobId, String workerId, Duration extension) {
        Instant now = Instant.now();
        Instant extendedUntil = now.plus(extension);
        int rows = jobRepository.heartbeat(jobId, workerId, now, extendedUntil);
        return rows > 0;
    }

    @Override
    public void completeJob(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(IngestionStatus.SUCCEEDED.name());
            job.setFinishedAt(Instant.now());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
            log.info("Job id={} marked as SUCCEEDED", jobId);
        });
    }

    @Override
    public void failJob(UUID jobId, IngestionErrorCode code, String message) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(IngestionStatus.FAILED.name());
            job.setFinishedAt(Instant.now());
            job.setErrorCode(code.name());
            job.setErrorMessage(message != null ? message : code.getDescription());
            job.setErrorType(ErrorClassification.PERMANENT.name());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);

            metrics.recordFailure(code.name());
            log.warn("Job id={} marked as FAILED with code={}: {}", jobId, code, message);
        });
    }

    @Override
    public void scheduleRetry(UUID jobId, IngestionErrorCode code, String message, Duration backoff) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getAttempts() >= job.getMaxAttempts()) {
                log.warn("Job id={} exceeded max attempts ({}), marking FAILED", jobId, job.getMaxAttempts());
                failJob(jobId, code, "Exceeded max attempts (" + job.getMaxAttempts() + "): " + message);
                return;
            }

            // Exponential backoff with jitter calculation
            long calculatedBackoffMs = Math.min(maxBackoffMs, baseBackoffMs * (1L << Math.min(job.getAttempts(), 10)));
            long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, calculatedBackoffMs / 4));
            long totalDelayMs = calculatedBackoffMs + jitter;

            Instant nextAttempt = Instant.now().plusMillis(totalDelayMs);

            job.setStatus(IngestionStatus.RETRY_SCHEDULED.name());
            job.setErrorCode(code.name());
            job.setErrorMessage(message != null ? message : code.getDescription());
            job.setErrorType(ErrorClassification.TRANSIENT.name());
            job.setNextAttemptAt(nextAttempt);
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);

            metrics.recordRetry();
            log.info("Job id={} scheduled for retry at {} with backoff {}ms", jobId, nextAttempt, totalDelayMs);
        });
    }
}
