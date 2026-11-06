# ADR-005: Chunking Strategy, Embedding Model, and Re-Index Policy

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

After a document is uploaded and approved, it enters the ingestion pipeline: parsing →
chunking → embedding → storage. The quality of the chunking and embedding directly determines
the quality of search results and copilot answers. We need decisions on:

1. **How to split documents into chunks** — granularity, overlap, context preservation.
2. **Which embedding model to use** — dimensions, cost, quality.
3. **How to handle re-indexing** when the chunking strategy or embedding model changes.
4. **How to avoid redundant work** when a document hasn't changed.

## Decision

### Chunking Strategy: Heading/Paragraph-Aware

Documents are split using a **structure-aware chunker** that respects document boundaries:

```
┌─────────────────────────────┐
│  PDF / DOCX / TXT / MD      │
│                             │
│  ┌───────────────────────┐  │
│  │ Parser (PDFBox/Tika)  │  │
│  │ → structured text     │  │
│  │   with headings,      │  │
│  │   paragraphs, pages   │  │
│  └───────────┬───────────┘  │
│              ▼              │
│  ┌───────────────────────┐  │
│  │ Chunker               │  │
│  │ → heading-aware split │  │
│  │ → ~512 tokens target  │  │
│  │ → ~64 tokens overlap  │  │
│  └───────────┬───────────┘  │
│              ▼              │
│  ┌───────────────────────┐  │
│  │ Chunks with metadata  │  │
│  │ → page numbers        │  │
│  │ → heading hierarchy   │  │
│  │ → chunk index         │  │
│  │ → content hash        │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

#### Chunking Parameters

| Parameter | Value | Configurable |
|-----------|-------|-------------|
| Target chunk size | ~512 tokens | `knowflow.ingestion.chunk.target-tokens` |
| Overlap | ~64 tokens | `knowflow.ingestion.chunk.overlap-tokens` |
| Min chunk size | 50 tokens | `knowflow.ingestion.chunk.min-tokens` |
| Max chunk size | 1024 tokens | `knowflow.ingestion.chunk.max-tokens` |
| Token estimator | `text.length() / 4` (approximate) | — |

#### Chunking Rules

1. **Prefer heading boundaries:** If a heading is encountered, start a new chunk even if the
   current chunk is below the target size (unless it would be below `min-tokens`).
2. **Prefer paragraph boundaries:** Within a section, split at paragraph breaks rather than
   mid-sentence.
3. **Preserve context with overlap:** The last ~64 tokens of a chunk are repeated as the first
   ~64 tokens of the next chunk to preserve boundary context.
4. **Attach heading hierarchy:** Each chunk includes its parent heading(s) as metadata, stored
   in the `heading_context` column. This is prepended to the chunk content before embedding to
   improve retrieval quality.
5. **Track page numbers:** For PDFs, each chunk records the `start_page` and `end_page`
   (1-indexed) from which it was extracted.
6. **Never split tables or code blocks** if they fit within `max-tokens`.

### Embedding Model

| Property | Value |
|----------|-------|
| Default model | Configurable (e.g., `text-embedding-3-small` or equivalent) |
| Dimensions | 1536 (stored as constant `EmbeddingConstants.DIMENSIONS`) |
| Configuration | `knowflow.ai.embedding.model` and `knowflow.ai.embedding.dimensions` |
| Provider | Via Spring AI `EmbeddingModel`, wrapped by `EmbeddingService` interface |

#### Startup Validation

At application startup, the `EmbeddingService` performs a test embedding of a known string
and validates:

1. The response has exactly `knowflow.ai.embedding.dimensions` dimensions.
2. The response is received within a timeout (5 seconds).
3. If validation fails, the application logs a clear error and disables AI features
   (does not prevent startup — keyword search must still work).

### Content Hashing for Deduplication

Each chunk has a content hash:

```java
String contentHash = SHA256.hash(normalizeWhitespace(chunkContent));
```

The hash is stored in `document_chunks.content_hash`. During re-ingestion:

- If a chunk's content hash matches an existing chunk for the same document version,
  skip re-embedding.
- This prevents wasting embedding API calls on unchanged content.

### Pipeline Versioning

Each chunk records the pipeline version that produced it:

```sql
ALTER TABLE document_chunks
    ADD COLUMN pipeline_version INTEGER NOT NULL DEFAULT 1;
```

The current pipeline version is a constant in `IngestionConstants.PIPELINE_VERSION`,
incremented when:

- The chunking algorithm changes.
- The embedding model changes.
- The embedding dimensions change.
- The overlap or target size changes significantly.

### Re-Index Policy

When the pipeline version changes, re-indexing is **explicit and safe**:

```
┌─────────────────────────────────────────────────┐
│  Re-Index Process                                │
│                                                  │
│  1. Admin triggers re-index (per document or     │
│     bulk) via API or scheduled job               │
│  2. New chunks created with new pipeline_version │
│     alongside existing chunks                    │
│  3. Search queries filter by active              │
│     pipeline_version for the document            │
│  4. Admin verifies new results quality           │
│  5. Admin switches active version atomically     │
│     (UPDATE documents SET active_pipeline_version│
│     = :new WHERE id = :docId)                    │
│  6. Old chunks cleaned up by background job      │
│     after verification period (default: 7 days)  │
└─────────────────────────────────────────────────┘
```

Key properties of re-indexing:

| Property | Guarantee |
|----------|-----------|
| No silent re-embedding | Pipeline version changes require explicit admin action |
| Zero-downtime | Old and new chunks coexist; search uses active version |
| Rollback | If new chunks produce worse results, keep the old version |
| Idempotent | Re-running re-index for a document that's already at the current version is a no-op |

### Chunk Metadata Schema

```sql
CREATE TABLE document_chunks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID NOT NULL REFERENCES documents(id),
    document_version_id UUID NOT NULL REFERENCES document_versions(id),
    chunk_index         INTEGER NOT NULL,
    content             TEXT NOT NULL,
    content_hash        VARCHAR(64) NOT NULL,
    heading_context     TEXT,          -- parent headings, prepended before embedding
    start_page          INTEGER,       -- 1-indexed, nullable for non-PDF
    end_page            INTEGER,
    token_count         INTEGER NOT NULL,
    embedding           vector(1536),  -- null if embedding failed or AI disabled
    pipeline_version    INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (document_version_id, chunk_index, pipeline_version)
);
```

## Alternatives Considered

### Fixed-Size Chunking (e.g., Every 512 Tokens)

| Aspect | Assessment |
|--------|------------|
| Pros | Simple to implement. Predictable chunk sizes. |
| Cons | Splits mid-sentence and mid-paragraph. Loses structural context. Heading information is separated from its content. |
| Verdict | **Rejected.** Structure-aware chunking produces significantly better retrieval quality with minimal additional complexity. |

### Sentence-Level Chunking

| Aspect | Assessment |
|--------|------------|
| Pros | Preserves sentence boundaries perfectly. |
| Cons | Individual sentences are too short for meaningful embeddings. Requires sentence grouping logic that essentially recreates paragraph-aware chunking. Many more chunks = more storage and slower search. |
| Verdict | **Rejected.** Too granular. Paragraph-level chunking with heading awareness is the right abstraction. |

### No Overlap Between Chunks

| Aspect | Assessment |
|--------|------------|
| Pros | Simpler. Less storage. |
| Cons | Information at chunk boundaries is lost. A query about content that spans a chunk boundary will have poor retrieval. |
| Verdict | **Rejected.** The ~12% storage overhead from 64-token overlap is worth the retrieval quality improvement. |

### Larger Embedding Model (e.g., 3072 dimensions)

| Aspect | Assessment |
|--------|------------|
| Pros | Potentially better semantic discrimination. |
| Cons | 2x storage for vectors. 2x computation for similarity search. pgvector HNSW index is larger and slower. Marginal quality improvement for our use case. |
| Verdict | **Rejected for initial deployment.** Start with 1536 dimensions. The `knowflow.ai.embedding.dimensions` config and pipeline versioning allow a future upgrade. |

## Consequences

### Positive

- **High retrieval quality:** Heading context + paragraph boundaries + overlap produce
  chunks that are semantically coherent.
- **Cost-efficient:** Content hashing prevents re-embedding unchanged content.
- **Safe upgrades:** Pipeline versioning allows zero-downtime model or strategy changes
  with rollback capability.
- **Traceable citations:** Page numbers and heading context enable precise citations in
  copilot answers.

### Negative

- **Complexity:** Structure-aware chunking is more complex than fixed-size splitting.
  Edge cases (nested headings, mixed content, tables) require careful handling.
- **Parser dependency:** Heading detection quality depends on the PDF/DOCX parser's ability
  to identify structural elements. Poorly formatted documents may fall back to
  paragraph-only chunking.
- **Re-index cost:** Changing the pipeline version for a large corpus requires significant
  embedding API calls and time.

### Obligations

- [ ] Implement `DocumentChunker` with heading-aware and paragraph-aware splitting.
- [ ] Write unit tests for chunking edge cases: empty sections, very long paragraphs,
      tables, code blocks, documents with no headings.
- [ ] Implement startup validation for embedding model dimensions.
- [ ] Create admin API endpoint for triggering re-index operations.
- [ ] Add `pipeline_version` to the chunk query filter in `SearchService`.
- [ ] Document the re-index process in an operations runbook.

---

*Cross-references:* [ADR-004](ADR-004-postgresql-search-pgvector.md) (search infrastructure),
[ADR-006](ADR-006-llm-provider.md) (embedding provider),
[ADR-007](ADR-007-database-backed-job-queue.md) (ingestion job execution)
