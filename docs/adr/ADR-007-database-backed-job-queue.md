# ADR-007: Database-Backed Job Queue + When to Add a Broker

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

KnowFlow's document ingestion pipeline involves long-running work: parsing documents (seconds),
chunking content (milliseconds to seconds), and generating embeddings via external API calls
(seconds to minutes depending on document size). Architecture Rule #6 mandates:

> Long-running work (extract, chunk, embed) NEVER runs in an HTTP request. Jobs are
> idempotent, retryable with backoff, and observable.

We need a job execution mechanism that:

1. Survives application restarts (durable).
2. Supports retry with backoff for transient failures (API rate limits, network issues).
3. Allows multiple worker instances to process jobs concurrently without conflicts.
4. Is observable (job status, progress, error details).
5. Does not require additional infrastructure (broker) unless proven necessary.

## Decision

### Database-Backed Job Table

Use a PostgreSQL table as the job queue:

```sql
CREATE TABLE ingestion_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id),
    version_id      UUID NOT NULL REFERENCES document_versions(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    job_type        VARCHAR(50) NOT NULL,          -- PARSE, CHUNK, EMBED, FULL_INGEST
    priority        INTEGER NOT NULL DEFAULT 0,     -- higher = more urgent
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    max_attempts    INTEGER NOT NULL DEFAULT 3,
    error_message   TEXT,
    error_category  VARCHAR(20),                   -- TRANSIENT, PERMANENT
    locked_by       VARCHAR(100),                  -- worker instance ID
    locked_at       TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_job_status CHECK (status IN (
        'PENDING', 'LOCKED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD'
    ))
);

CREATE INDEX idx_jobs_claimable
    ON ingestion_jobs (next_attempt_at, priority DESC)
    WHERE status IN ('PENDING');

CREATE INDEX idx_jobs_document
    ON ingestion_jobs (document_id, status);
```

### Job State Machine

```
                    ┌──────────────┐
                    │   PENDING    │
                    └──────┬───────┘
                           │ Worker claims (SELECT FOR UPDATE SKIP LOCKED)
                           ▼
                    ┌──────────────┐
                    │   LOCKED     │
                    └──────┬───────┘
                           │ Worker starts processing
                           ▼
                    ┌──────────────┐
              ┌─────│   RUNNING    │─────┐
              │     └──────────────┘     │
              │ Success                  │ Failure
              ▼                          ▼
       ┌──────────────┐          ┌──────────────┐
       │  COMPLETED   │          │  (evaluate)  │
       └──────────────┘          └──────┬───────┘
                                        │
                              ┌─────────┴─────────┐
                              │                   │
                    Transient + retries left   Permanent or max retries
                              │                   │
                              ▼                   ▼
                       ┌──────────────┐    ┌──────────────┐
                       │   PENDING    │    │    DEAD      │
                       │ (retry)      │    │              │
                       └──────────────┘    └──────────────┘
```

### Job Claiming: SELECT ... FOR UPDATE SKIP LOCKED

Workers claim jobs using PostgreSQL's `SKIP LOCKED` to prevent contention:

```sql
-- Claim up to N jobs atomically
UPDATE ingestion_jobs
SET status = 'LOCKED',
    locked_by = :workerId,
    locked_at = NOW(),
    attempt_count = attempt_count + 1
WHERE id IN (
    SELECT id FROM ingestion_jobs
    WHERE status = 'PENDING'
      AND next_attempt_at <= NOW()
    ORDER BY priority DESC, created_at ASC
    FOR UPDATE SKIP LOCKED
    LIMIT :batchSize
)
RETURNING *;
```

Properties:

| Property | Guarantee |
|----------|-----------|
| No double-processing | `FOR UPDATE SKIP LOCKED` ensures at most one worker claims a job |
| Fair ordering | Priority first, then FIFO within same priority |
| Stale lock detection | Background job reclaims jobs where `locked_at` > 10 minutes ago and status is `LOCKED` |

### Retry Strategy: Exponential Backoff with Jitter

```java
Duration nextDelay(int attemptCount) {
    long baseMs = 1000L * (long) Math.pow(2, attemptCount);  // 2s, 4s, 8s, 16s, ...
    long jitterMs = ThreadLocalRandom.current().nextLong(0, baseMs / 2);
    long delayMs = Math.min(baseMs + jitterMs, MAX_DELAY_MS);  // cap at 5 minutes
    return Duration.ofMillis(delayMs);
}
```

| Attempt | Base Delay | With Jitter (approx) | Cumulative Wait |
|---------|-----------|----------------------|-----------------|
| 1 | 2s | 2–3s | ~3s |
| 2 | 4s | 4–6s | ~8s |
| 3 | 8s | 8–12s | ~18s |
| Max (default) | — | — | Job moves to DEAD |

### Error Classification

| Category | Examples | Retry? |
|----------|---------|--------|
| `TRANSIENT` | HTTP 429 (rate limit), HTTP 503 (provider down), network timeout, connection refused | ✅ Yes, with backoff |
| `PERMANENT` | HTTP 400 (malformed request), unsupported file format, corrupt file, parse error | ❌ No, immediately DEAD |

The `IngestionService` classifies errors using an `ErrorClassifier`:

```java
public interface ErrorClassifier {
    ErrorCategory classify(Exception e);
}
```

### Worker Execution Model

Workers run as **scheduled tasks within the same Spring Boot application**, activated by
Spring profiles:

```yaml
# application-worker.yml
knowflow:
  worker:
    enabled: true
    poll-interval: 5s       # How often to check for new jobs
    batch-size: 5            # Jobs to claim per poll
    thread-pool-size: 3      # Concurrent job processing threads
    stale-lock-timeout: 10m  # Reclaim jobs locked longer than this
```

| Deployment Mode | Configuration |
|----------------|---------------|
| All-in-one (dev) | `spring.profiles.active=local` — web + worker in one process |
| Separate worker (prod) | Process 1: `spring.profiles.active=prod,web` — serves HTTP. Process 2: `spring.profiles.active=prod,worker` — processes jobs only. |

### JobDispatcher Interface

For future replacement with a message broker:

```java
public interface JobDispatcher {
    /**
     * Submit a job for async processing.
     */
    void dispatch(IngestionJobRequest request);

    /**
     * Cancel a pending job.
     */
    boolean cancel(UUID jobId);

    /**
     * Query job status.
     */
    JobStatus getStatus(UUID jobId);
}
```

Current implementation: `DatabaseJobDispatcher` writes to the `ingestion_jobs` table.
Future implementation: `RabbitMqJobDispatcher` or similar.

### Idempotency

Jobs are idempotent by design:

1. **Parse:** Re-parsing the same document version produces the same structured text.
   Existing parsed content is replaced.
2. **Chunk:** Re-chunking replaces existing chunks for the same `(document_version_id,
   pipeline_version)`. Content hashing prevents redundant embedding (see ADR-005).
3. **Embed:** Content hash comparison skips embedding for unchanged chunks.

A job can safely be retried any number of times without producing duplicate or inconsistent
data.

### Observability

| Metric | Source |
|--------|--------|
| Jobs pending count | `SELECT COUNT(*) FROM ingestion_jobs WHERE status = 'PENDING'` |
| Jobs running count | `SELECT COUNT(*) FROM ingestion_jobs WHERE status = 'RUNNING'` |
| Jobs failed (last hour) | `SELECT COUNT(*) FROM ingestion_jobs WHERE status = 'DEAD' AND updated_at > NOW() - INTERVAL '1 hour'` |
| Average job duration | `SELECT AVG(completed_at - started_at) FROM ingestion_jobs WHERE status = 'COMPLETED'` |
| Stale locks | `SELECT COUNT(*) FROM ingestion_jobs WHERE status = 'LOCKED' AND locked_at < NOW() - INTERVAL '10 minutes'` |

These are exposed via:

- **Spring Actuator metrics** (`/actuator/metrics/knowflow.jobs.*`)
- **Admin API** (`GET /api/admin/jobs/stats`)
- **Health indicator** (unhealthy if stale lock count > 0 or dead job rate > threshold)

## Alternatives Considered

### Spring Batch

| Aspect | Assessment |
|--------|------------|
| Pros | Mature, well-tested. Restart and retry built in. Partitioned processing. |
| Cons | Heavier framework than needed. Requires its own metadata tables (6+). Step-oriented model doesn't fit well with our simple job types. Learning curve for configuration. |
| Verdict | **Rejected.** Our job model is simple (one job = one document version through the pipeline). A custom job table with `SKIP LOCKED` is lighter and more transparent. |

### RabbitMQ / Kafka from Day 1

| Aspect | Assessment |
|--------|------------|
| Pros | Purpose-built for async messaging. High throughput. Decoupled producers/consumers. |
| Cons | Additional infrastructure: broker deployment, monitoring, alerting. Message ordering complexity. Dead letter queue management. Consumer group configuration. All of this for a system that initially processes dozens of documents per day. |
| Verdict | **Rejected.** Premature complexity. The `JobDispatcher` interface allows migration when needed. |

### In-Memory Queue (Java BlockingQueue)

| Aspect | Assessment |
|--------|------------|
| Pros | Zero setup. Fast. No external dependencies. |
| Cons | **Not durable.** All pending jobs lost on restart. No visibility into queue state. No cross-instance claiming. |
| Verdict | **Rejected.** Durability is a hard requirement. Users must not need to re-upload documents after a restart. |

## Broker Adoption Criteria

Add a message broker (RabbitMQ recommended) **only when** at least one of these is true:

| Criterion | Threshold | Measurement |
|-----------|-----------|-------------|
| Job throughput | > 100 jobs/minute sustained | Actuator metrics |
| Poll overhead | `SELECT ... SKIP LOCKED` queries consume > 5% of DB CPU | `pg_stat_statements` |
| Fan-out needed | A job completion must notify 3+ independent consumers | Architecture review |
| Independent consumers | Consumers in different languages or deployment units need the same events | Architecture review |

Document the measurement that triggered the migration.

## Consequences

### Positive

- **No additional infrastructure:** The database is already there. No broker to deploy,
  monitor, or secure.
- **Transactional consistency:** Job creation and document metadata updates are in the same
  transaction.
- **Full visibility:** Job state is queryable with SQL. Debugging is straightforward.
- **Durable:** Jobs survive application restarts.
- **Idempotent:** Safe to retry, safe to run multiple workers.

### Negative

- **Polling overhead:** Workers poll the database every N seconds even when there are no jobs.
  Mitigated by adaptive polling (back off when queue is empty).
- **Scale ceiling:** PostgreSQL `SKIP LOCKED` throughput is finite. Adequate for our scale
  but won't match a dedicated broker.
- **No fan-out:** One job is claimed by one worker. Publish-subscribe patterns require
  application-level event publishing.

### Obligations

- [ ] Create `ingestion_jobs` table via Flyway migration.
- [ ] Implement `DatabaseJobDispatcher` with `SKIP LOCKED` claiming.
- [ ] Implement stale lock detection and reclamation scheduled task.
- [ ] Write integration tests: job claiming, retry with backoff, error classification,
      idempotency, stale lock recovery.
- [ ] Expose job metrics via Spring Actuator.
- [ ] Create admin API for job status inspection and dead job retry.
- [ ] Document worker deployment as Spring profile in operations guide.

---

*Cross-references:* [ADR-001](ADR-001-modular-monolith-architecture.md) (worker profiles),
[ADR-005](ADR-005-chunking-embedding.md) (ingestion pipeline stages),
[ADR-006](ADR-006-llm-provider.md) (embedding API calls that jobs make)
