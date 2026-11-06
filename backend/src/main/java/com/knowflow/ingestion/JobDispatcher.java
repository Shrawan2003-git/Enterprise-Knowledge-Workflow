package com.knowflow.ingestion;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface JobDispatcher {

    /**
     * Idempotently enqueues an ingestion job. If a job for the same (versionId, pipelineVersion)
     * already exists, the existing job is returned.
     */
    IngestionJob enqueue(UUID versionId, String pipelineVersion);

    /**
     * Claims up to `limit` pending or retryable jobs for the specified worker using atomic leasing.
     */
    List<IngestionJob> claimBatch(String workerId, Duration leaseDuration, int limit);

    /**
     * Renews the lease for a currently running job.
     */
    boolean heartbeat(UUID jobId, String workerId, Duration extension);

    /**
     * Marks the job as successfully SUCCEEDED.
     */
    void completeJob(UUID jobId);

    /**
     * Marks the job as permanently FAILED.
     */
    void failJob(UUID jobId, IngestionErrorCode code, String message);

    /**
     * Schedules a retry with exponential backoff and jitter.
     */
    void scheduleRetry(UUID jobId, IngestionErrorCode code, String message, Duration backoff);
}
