# ADR-008: Audit and Conversation Retention, Export, and Deletion

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

KnowFlow handles enterprise documents, approval workflows, and AI-generated answers. Multiple
compliance and operational concerns require decisions about data lifecycle:

1. **Audit trail:** Who did what, when? Required for compliance, incident investigation, and
   non-repudiation. Architecture Rule #12 mandates append-only audit.
2. **Conversation data:** AI copilot conversations contain user questions and AI-generated
   answers based on authorized documents. Privacy, storage, and access control are concerns.
3. **Data retention:** How long to keep different data types. Too long = storage and privacy
   risk. Too short = compliance risk.
4. **Data export:** Compliance teams need audit log exports.
5. **Data deletion:** GDPR-style "right to deletion" and storage management.

## Decision

### Audit Logs: Append-Only

Audit logs are **append-only at the application layer**:

```java
// AuditLogRepository — NO update or delete methods
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    // Only save() and find*() methods
    // No deleteById(), no deleteAll(), no custom @Modifying UPDATE queries
}
```

Enforcement:

- The `AuditLogRepository` interface explicitly does **not** extend `JpaRepository` methods
  for delete/update. It extends a custom `AppendOnlyRepository` base.
- An ArchUnit test verifies that no class in the `audit` module calls `delete*` or `update*`
  methods on audit entities.
- Database-level: Consider a trigger or `REVOKE DELETE, UPDATE ON audit_logs FROM app_user`
  when using a least-privilege DB role.

#### Audit Log Schema

```sql
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id        UUID REFERENCES users(id),          -- NULL for system actions
    actor_email     VARCHAR(255),                        -- denormalized for query after user deletion
    action          VARCHAR(100) NOT NULL,               -- e.g., DOCUMENT_UPLOADED, APPROVAL_GRANTED
    resource_type   VARCHAR(50) NOT NULL,                -- e.g., DOCUMENT, USER, APPROVAL
    resource_id     UUID,
    module          VARCHAR(50) NOT NULL,                -- e.g., documents, approvals, identity
    detail          JSONB,                               -- structured action-specific data
    ip_address      VARCHAR(45),                         -- IPv4 or IPv6
    correlation_id  UUID NOT NULL                        -- ties to request/response cycle
);

CREATE INDEX idx_audit_timestamp ON audit_logs (timestamp);
CREATE INDEX idx_audit_actor ON audit_logs (actor_id, timestamp);
CREATE INDEX idx_audit_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_action ON audit_logs (action, timestamp);
```

#### Audited Actions

| Module | Actions Audited |
|--------|----------------|
| identity | `USER_CREATED`, `USER_UPDATED`, `USER_DEACTIVATED`, `USER_ROLE_CHANGED`, `LOGIN_SUCCESS`, `LOGIN_FAILED`, `PASSWORD_CHANGED`, `TOKENS_REVOKED` |
| documents | `DOCUMENT_UPLOADED`, `DOCUMENT_UPDATED`, `DOCUMENT_DOWNLOADED`, `DOCUMENT_DELETED`, `DOCUMENT_ACCESS_CHANGED` |
| approvals | `APPROVAL_REQUESTED`, `APPROVAL_GRANTED`, `APPROVAL_REJECTED`, `APPROVAL_REVOKED` |
| copilot | `COPILOT_QUERY`, `COPILOT_RESPONSE` (without full content — only metadata), `CONVERSATION_DELETED` |
| admin | `CONFIG_CHANGED`, `REINDEX_TRIGGERED`, `BULK_OPERATION`, `ADMIN_CONVERSATION_ACCESS` |
| ingestion | `INGESTION_STARTED`, `INGESTION_COMPLETED`, `INGESTION_FAILED` |

**What is NOT logged:**

- Full document content (too large, sensitive).
- Full copilot prompts/responses (privacy; only metadata like token count, model, doc IDs cited).
- Passwords, tokens, or secrets (Architecture Rule #10).

### Conversation Retention and Access

#### Ownership

- Conversations **belong to the user who created them** (`conversations.owner_id`).
- Users can view, continue, and delete their own conversations.
- By default, **no one else** can access a user's conversations, including admins.

#### Admin Access (Opt-In, Audited)

```yaml
knowflow:
  admin:
    conversation-access:
      enabled: false  # Default: OFF
```

When `enabled: true`:

- Admins can access other users' conversations via `GET /api/admin/conversations`.
- **Every admin access is audited** with action `ADMIN_CONVERSATION_ACCESS`, recording:
  - Admin user ID
  - Target user ID
  - Conversation ID
  - Timestamp
  - Justification field (required, free text)
- The accessed user is **not** notified (to avoid alert fatigue), but the audit log is
  available for compliance review.

When `enabled: false` (default):

- Admin conversation access endpoints return `403 Forbidden`.
- No code path exists to bypass this flag.

#### Conversation Deletion

Users can delete their own conversations:

```
DELETE /api/conversations/{conversationId}
```

This performs:

1. Verify ownership (`conversation.owner_id == currentUser.id`).
2. Soft-delete the conversation (set `deleted_at` timestamp).
3. Create audit log entry `CONVERSATION_DELETED`.
4. Soft-deleted conversations are excluded from all queries.
5. Hard deletion occurs during retention cleanup (see below).

### Retention Policy

| Data Type | Default Retention | Configurable | Rationale |
|-----------|------------------|-------------|-----------|
| Audit logs | 2 years | `knowflow.retention.audit-logs-months: 24` | Compliance, incident investigation |
| Conversations (active) | 1 year from last activity | `knowflow.retention.conversations-months: 12` | Storage management, privacy |
| Conversations (soft-deleted) | 30 days | `knowflow.retention.deleted-conversations-days: 30` | Grace period for accidental deletion |
| Feedback records | Indefinite | — | Needed for model evaluation and quality tracking |
| Document versions | Follow org retention policy | `knowflow.retention.document-versions-years: 7` | Legal and compliance |
| Embeddings and chunks | Derived data — tied to their document version | — | Can be rebuilt from source documents |
| Ingestion job records | 90 days (completed), indefinite (failed) | `knowflow.retention.completed-jobs-days: 90` | Debugging, completed jobs are noise after a while |

### Retention Cleanup Job

A scheduled job runs the cleanup:

```java
@Scheduled(cron = "0 0 3 * * *")  // Daily at 3 AM UTC
public void cleanupExpiredRecords() {
    int auditDeleted = auditRetentionService.purgeExpired();
    int convoDeleted = conversationRetentionService.purgeExpired();
    int jobsDeleted = jobRetentionService.purgeCompleted();

    log.info("Retention cleanup: audit={}, conversations={}, jobs={}",
             auditDeleted, convoDeleted, jobsDeleted);

    auditService.log(AuditAction.RETENTION_CLEANUP, Map.of(
        "auditRecordsPurged", auditDeleted,
        "conversationsPurged", convoDeleted,
        "jobsPurged", jobsDeleted
    ));
}
```

Properties:

| Property | Value |
|----------|-------|
| Batch size | 1000 records per transaction (avoid long locks) |
| Method | Hard DELETE for expired records |
| Audit | The cleanup job itself is audited |
| Idempotent | Safe to run multiple times |

### Derived Data (Embeddings and Chunks)

Embeddings and chunks are **derived data** — they are generated from source documents and can
be rebuilt at any time:

- Deleting a document version's chunks and embeddings does **not** lose information (the
  source file is in `FileStorage`).
- Re-indexing (ADR-005) regenerates chunks and embeddings.
- During document hard deletion, associated chunks and embeddings are cascade-deleted.

### Audit Log Export

Admin API for compliance export:

```
GET /api/admin/audit/export
    ?startDate=2026-01-01T00:00:00Z
    &endDate=2026-10-01T00:00:00Z
    &format=csv       # or json
    &module=approvals  # optional filter
    &action=APPROVAL_GRANTED  # optional filter
```

Response: Streamed download (chunked transfer encoding) to handle large exports without
OOM errors.

| Format | MIME Type | Notes |
|--------|-----------|-------|
| CSV | `text/csv` | One row per audit entry; JSONB `detail` flattened to string |
| JSON | `application/json` | Array of audit entries; streaming JSON array |

Export requests are themselves audited (`AUDIT_EXPORT` action).

### Document Version Immutability

Document versions are **immutable** once created (Architecture Rule #12):

- A new upload creates a new `document_version` record; it does not modify the existing one.
- The file in `FileStorage` is never overwritten.
- Version history is preserved for the lifetime of the document.
- Hard deletion of a document removes all its versions (subject to retention policy).

## Alternatives Considered

### No Retention Limits (Keep Everything)

| Aspect | Assessment |
|--------|------------|
| Pros | Simplest. No data loss risk. Maximum auditability. |
| Cons | Unbounded storage growth. Privacy risk (conversations accumulate). GDPR-style regulations may require deletion capabilities. Performance degradation as tables grow. |
| Verdict | **Rejected.** Reasonable retention limits balance compliance with privacy and operational concerns. |

### Hard-Delete Everything Immediately

| Aspect | Assessment |
|--------|------------|
| Pros | Privacy-maximizing. Minimal storage. |
| Cons | Compliance risk — many regulations require audit trail retention for 1-7 years. Incident investigation becomes impossible. No undo for accidental deletions. |
| Verdict | **Rejected.** Append-only audit with defined retention periods is the industry standard approach. |

### External Audit System (ELK, Splunk)

| Aspect | Assessment |
|--------|------------|
| Pros | Purpose-built for log analysis. Scalable. Rich query capabilities. |
| Cons | Additional infrastructure. Data consistency between PG and external system. More complex deployment. Solo developer operational burden. |
| Verdict | **Rejected for initial deployment.** PostgreSQL audit table with proper indexing is sufficient. The structured `JSONB detail` field provides queryability. Can export to external systems later if needed. |

## Consequences

### Positive

- **Compliance-ready:** Append-only audit with defined retention meets common regulatory
  requirements.
- **Privacy-respecting:** Conversation data has clear ownership, access controls, and
  deletion capabilities.
- **Operationally sustainable:** Retention cleanup prevents unbounded storage growth.
- **Transparent:** Admin conversation access is opt-in and fully audited.
- **Exportable:** CSV/JSON export enables compliance reporting without direct database access.

### Negative

- **Retention complexity:** The cleanup job must handle edge cases (foreign key dependencies,
  concurrent access, partial failures).
- **Hard-delete irreversibility:** Once retention cleanup runs, data is permanently gone.
  Mitigated by database backups and the soft-delete grace period.
- **Audit table growth:** High-activity systems may accumulate millions of audit records
  before the 2-year retention kicks in. Mitigated by proper indexing and partitioning if
  needed.

### Obligations

- [ ] Create `audit_logs` table via Flyway migration.
- [ ] Implement `AppendOnlyRepository` base interface (no delete/update methods).
- [ ] Write ArchUnit test: no delete/update calls on audit entities.
- [ ] Implement retention cleanup scheduled job with batch processing.
- [ ] Create admin export endpoint with streaming response.
- [ ] Write integration tests for: audit log creation, retention cleanup, export,
      admin conversation access (enabled/disabled), conversation deletion.
- [ ] Add `ADMIN_CONVERSATION_ACCESS` audit action and required justification field.
- [ ] Document retention defaults in operations guide.

---

*Cross-references:* [ADR-001](ADR-001-modular-monolith-architecture.md) (audit module),
[ADR-002](ADR-002-authentication-local-jwt.md) (login auditing),
[ADR-003](ADR-003-file-storage.md) (file retention),
[threat-model.md](../threat-model.md) (data exposure threats)
