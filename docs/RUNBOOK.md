# KnowFlow Operational Runbook & Incident Response Guide

## 1. Overview & Architecture Summary

KnowFlow is a mission-critical enterprise knowledge copilot.
Its core design principle is **graceful degradation**:
- The application remains 100% operational for document management, approval workflows, task tracking, and full-text keyword search even when external AI providers are completely unavailable.
- Ingestion jobs are asynchronous, idempotent, and retryable with exponential backoff.
- Authorization is enforced in database queries, preventing data leakage regardless of upstream system state.

---

## 2. Emergency Incident Procedures

### Incident 1: External AI Provider Outage (OpenAI / Azure 5xx / 429)

**Symptoms:**
- Metric `knowflow.copilot.timeouts` or `knowflow.copilot.rate_limit.exceeded` spiking.
- Copilot returns fallback messages or degraded status.
- Document ingestion stalls at the `EMBED` step.

**Action Plan:**
1. **Verify Degradation Mode:**
   - Ingestion automatically completes up to chunking and tsvector indexing.
   - Document versions transition safely to `READY_KEYWORD_ONLY` if AI embedding is disabled or unavailable.
2. **Temporary Fallback Activation:**
   - Toggle AI off dynamically via environment variable or application config:
     ```bash
     KNOWFLOW_AI_ENABLED=false
     ```
   - Restart the backend pods/containers:
     ```bash
     docker compose -f deploy/docker-compose.prod.yml restart backend
     ```
   - Core search and document workflows continue without interruption.
3. **Recovery:**
   - Once provider status green, re-enable `KNOWFLOW_AI_ENABLED=true`.
   - Run the re-embed batch job to backfill pending vectors for `READY_KEYWORD_ONLY` versions.

---

### Incident 2: Ingestion Queue Backlog / Worker Starvation

**Symptoms:**
- Ingestion queue depth gauge (`knowflow.ingestion.queue.depth`) consistently increasing.
- Users report document status staying in `EXTRACTING` or `CHUNKING` for > 5 minutes.

**Diagnosis:**
```sql
SELECT status, error_category, attempts, COUNT(*) 
FROM ingestion_jobs 
GROUP BY status, error_category, attempts;
```

**Action Plan:**
1. **Identify Stuck Leases:**
   - Worker crashes may leave jobs in `RUNNING` past lease timeout (default 60s).
   - Ingestion coordinator automatically reclaims expired leases on next polling cycle.
2. **Scale Worker Concurrency:**
   - Increase ingestion concurrency:
     ```bash
     KNOWFLOW_INGESTION_CONCURRENCY=8
     ```
3. **Dead Letter Investigation:**
   - Check jobs marked `FAILED`:
     ```sql
     SELECT id, document_id, step, last_error_message, updated_at 
     FROM ingestion_jobs 
     WHERE status = 'FAILED' 
     ORDER BY updated_at DESC LIMIT 10;
     ```
   - Reset transient failures:
     ```sql
     UPDATE ingestion_jobs 
     SET status = 'PENDING', attempts = 0, last_error_message = NULL 
     WHERE status = 'FAILED' AND error_category = 'TRANSIENT';
     ```

---

### Incident 3: Corrupt or Malformed PDF Upload

**Symptoms:**
- Parser failure during Apache PDFBox extraction: `PDF_PARSE_FAILED`.
- Corrupt file stored in object storage.

**Action Plan:**
1. Ingestion isolates parse failures to the specific job without crashing the worker.
2. The document version is tagged with ingestion status `FAILED` and error reason recorded in `ingestion_jobs`.
3. Notification dispatched to document submitter requesting a clean re-upload.
4. Clean up orphaned storage objects:
   ```bash
   # Storage cleanup can be performed via document deletion API
   DELETE /api/v1/documents/{documentId}
   ```

---

### Incident 4: Database Storage & Vector Index Capacity Warning

**Symptoms:**
- PostgreSQL disk usage exceeding 80%.
- Vector index maintenance memory alerts.

**Action Plan:**
1. **Analyze Table & Index Sizes:**
   ```sql
   SELECT relname, pg_size_pretty(pg_total_relation_size(c.oid))
   FROM pg_class c
   JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'public'
   ORDER BY pg_total_relation_size(c.oid) DESC;
   ```
2. **Vacuum & Reindex Vector Graphs:**
   ```sql
   VACUUM ANALYZE chunk_embeddings;
   REINDEX INDEX CONCURRENTLY idx_chunk_embeddings_hnsw;
   ```
3. **Run Conversation Retention Purge:**
   - Execute the ADR-008 retention job to purge conversations older than retention limit (default 90 days):
   ```sql
   SELECT COUNT(*) FROM conversations WHERE created_at < NOW() - INTERVAL '90 days';
   ```

---

### Incident 5: Leaked API Keys or Service Credentials

**Symptoms:**
- Suspicious outbound traffic or provider alert indicating compromised `OPENAI_API_KEY` or `KNOWFLOW_JWT_SECRET`.

**Action Plan:**
1. **Immediately Revoke & Rotate Keys:**
   - Generate new provider key in OpenAI/Azure portal.
   - Update environment secret in deployment configuration:
     ```bash
     OPENAI_API_KEY=<new_key>
     ```
2. **JWT Secret Rotation:**
   - Generate a new 256-bit secret:
     ```bash
     openssl rand -hex 32
     ```
   - Update `KNOWFLOW_JWT_SECRET`.
   - Note: Rotating the JWT secret immediately invalidates all active sessions, requiring users to log in again.
3. **Audit Verification:**
   - Inspect `audit_logs` and `tool_audit_logs` for unusual activity during the compromise window.

---

### Incident 6: Suspected Access Control / Authorization Incident

**Symptoms:**
- User reports seeing document titles or snippets from an unauthorized department.

**Action Plan:**
1. **Zero-Trust Verification Query:**
   - Test current permissions directly against the authorization policy:
     ```sql
     SELECT d.id, d.title, d.confidentiality_level, a.permission
     FROM documents d
     LEFT JOIN document_acls a ON d.id = a.document_id AND a.user_id = '<user_uuid>'
     WHERE d.id = '<suspected_doc_uuid>';
     ```
2. **Audit Inspection:**
   - Review all document downloads, searches, and copilot queries:
     ```sql
     SELECT timestamp, actor_id, action, target_id, details
     FROM audit_logs
     WHERE actor_id = '<user_uuid>' OR target_id = '<suspected_doc_uuid>'
     ORDER BY timestamp DESC LIMIT 50;
     ```
3. **Immediate Remediation:**
   - Revoke ACL or update confidentiality level via administrative API.
   - Vector search enforces ACLs directly in SQL on every query, so changes take effect immediately without re-indexing.

---

## 3. Disaster Recovery & Tested Restore Procedure

A scheduled backup runs daily at 02:00 UTC using `deploy/scripts/backup.sh`.
Backups are encrypted, gzipped, and stored offsite with 30-day retention.

### Step-by-Step Tested Restore Drill

1. **Prepare Isolated Target Environment:**
   ```bash
   docker compose -f deploy/docker-compose.prod.yml down -v
   docker compose -f deploy/docker-compose.prod.yml up -d postgres minio
   ```
2. **Run Restore Script:**
   ```bash
   ./deploy/scripts/restore.sh ./backups/knowflow_backup_latest.sql.gz
   ```
3. **Validation Checklist:**
   - [x] Flyway schema history intact and matching version count.
   - [x] `chunk_embeddings` table populated with valid vector cosine indices.
   - [x] Users and departments match expected production state.
   - [x] Healthcheck endpoint reports healthy readiness status:
     ```bash
     curl -i http://localhost:8080/actuator/health/readiness
     ```
