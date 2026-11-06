# ADR-003: File Storage — Local Volume (Dev) + S3-Compatible (Prod)

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

KnowFlow allows users to upload documents (PDF, DOCX, TXT, MD) that are parsed, chunked, and
embedded for search. These original files must be stored durably and served back for download.
Key requirements:

1. Original files are stored **outside the database** — the DB holds metadata only.
2. Storage must work seamlessly in local development and in production.
3. File paths must never be derived from user-supplied filenames (security risk: path traversal,
   collisions, encoding issues).
4. The storage mechanism must be swappable without changing business logic.
5. Files may range from a few KB (text) to 50 MB (large PDFs).

## Decision

### FileStorage Interface

All file operations go through a `FileStorage` interface in the `common` module:

```java
public interface FileStorage {
    /**
     * Store a file and return its storage key.
     * @param content the file input stream
     * @param contentLength the size in bytes
     * @param contentType the MIME type
     * @return a storage key (UUID-based path)
     */
    String store(InputStream content, long contentLength, String contentType);

    /**
     * Retrieve a file by its storage key.
     * @return the file content as an InputStream
     * @throws FileNotFoundException if the key does not exist
     */
    InputStream retrieve(String storageKey);

    /**
     * Delete a file by its storage key.
     */
    void delete(String storageKey);

    /**
     * Check if a file exists.
     */
    boolean exists(String storageKey);
}
```

### Storage Key Format

Keys are **UUID-based paths**, never derived from user filenames:

```
documents/{year}/{month}/{uuid}.{extension}
```

Example: `documents/2026/10/a3f1b2c4-5d6e-7f8a-9b0c-1d2e3f4a5b6c.pdf`

The original filename is stored in the `documents` table metadata, not in the storage path.

### Implementations

| Implementation | Activation | Use Case |
|---------------|------------|----------|
| `LocalFileStorage` | `spring.profiles.active=local` (default) | Development, testing |
| `S3FileStorage` | `spring.profiles.active=prod` or `storage.type=s3` | Production |

#### LocalFileStorage

- Base directory: configurable via `knowflow.storage.local.base-dir` (default: `./data/files`)
- Creates directories as needed
- No concurrent access guarantees beyond OS filesystem semantics (sufficient for single-instance dev)

#### S3FileStorage

- Uses AWS SDK v2 (compatible with MinIO, AWS S3, DigitalOcean Spaces)
- Configuration:

```yaml
knowflow:
  storage:
    s3:
      endpoint: ${STORAGE_S3_ENDPOINT}     # e.g., http://minio:9000
      region: ${STORAGE_S3_REGION:us-east-1}
      bucket: ${STORAGE_S3_BUCKET:knowflow-docs}
      access-key: ${STORAGE_S3_ACCESS_KEY}
      secret-key: ${STORAGE_S3_SECRET_KEY}
```

- Bucket existence validated at startup
- Server-side encryption enabled (SSE-S3) when available

### Upload Validation

Performed **before** calling `FileStorage.store()`:

| Check | Limit |
|-------|-------|
| Max file size | 50 MB (configurable via `knowflow.upload.max-size`) |
| Allowed MIME types | `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`, `text/markdown` |
| MIME type detection | Apache Tika (content-based, not extension-based) |
| Filename sanitization | Original filename stored as metadata; only alphanumeric + dots + hyphens preserved for display |

### File Lifecycle

```
Upload → Validate → Store (FileStorage) → Create DB record → Trigger ingestion job
                                                    │
                                          document_versions.storage_key
                                                    │
Download ← Authorize (DocumentAccessPolicy) ← Retrieve (FileStorage)
```

Files are **never deleted** when a document is soft-deleted. Hard deletion of files only
occurs:

1. During explicit admin purge operations.
2. When a document version is replaced (the old version's file is retained for audit — see
   ADR-008).

## Alternatives Considered

### Store Files as BLOBs in PostgreSQL

| Aspect | Assessment |
|--------|------------|
| Pros | Transactional consistency with metadata. Simple backup (just the DB). |
| Cons | Database backup size grows dramatically. PostgreSQL BLOB performance degrades at scale. DB connection held during download. Complicates streaming large files. |
| Verdict | **Rejected.** Files up to 50 MB would bloat the database. Object storage is the industry standard for file storage. |

### Git LFS

| Aspect | Assessment |
|--------|------------|
| Pros | Version history built-in. Familiar tooling. |
| Cons | Wrong abstraction for a web application. No streaming API. Requires Git operations for every file access. Not designed for concurrent multi-user writes. |
| Verdict | **Rejected.** Git LFS solves a different problem (large file tracking in repositories). |

### Cloud-Only Storage (No Local Option)

| Aspect | Assessment |
|--------|------------|
| Pros | Production-like behavior in all environments. |
| Cons | Requires network connectivity and cloud credentials for local development. Slows down the dev feedback loop. Makes offline development impossible. |
| Verdict | **Rejected.** The `FileStorage` interface lets us use local storage in dev and cloud storage in prod without code changes. MinIO in Docker Compose provides a local S3-compatible option for integration testing. |

## Consequences

### Positive

- **Clean separation:** Business logic never knows where files physically live.
- **Testable:** `LocalFileStorage` requires no external services. Integration tests use a
  temp directory.
- **Prod-ready:** S3-compatible storage is durable, scalable, and supports CDN integration
  if needed.
- **Secure paths:** UUID-based keys eliminate path traversal, filename collision, and encoding
  attacks.

### Negative

- **Two implementations to maintain:** Changes to the interface require updates to both
  `LocalFileStorage` and `S3FileStorage`.
- **No transactional guarantee:** If the DB transaction fails after `store()`, an orphan file
  exists in storage. Mitigated by a periodic cleanup job that reconciles storage keys against
  the database.
- **No built-in versioning:** File versioning is handled at the application level
  (`document_versions` table), not by the storage layer.

### Obligations

- [ ] Write integration tests for both `LocalFileStorage` and `S3FileStorage` (using
      Testcontainers with MinIO).
- [ ] Implement orphan file cleanup job (runs daily, compares storage keys against
      `document_versions.storage_key`).
- [ ] Validate bucket existence at startup for `S3FileStorage`.
- [ ] Never log storage keys alongside user-identifiable information in the same log line.
- [ ] Add content-type validation using Apache Tika, not file extension.

---

*Cross-references:* [ADR-001](ADR-001-modular-monolith-architecture.md) (module structure),
[ADR-005](ADR-005-chunking-embedding.md) (ingestion pipeline consumes stored files),
[ADR-008](ADR-008-audit-retention.md) (file retention policy)
