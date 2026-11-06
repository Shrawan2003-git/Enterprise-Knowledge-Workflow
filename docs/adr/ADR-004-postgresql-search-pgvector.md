# ADR-004: PostgreSQL Full-Text Search + pgvector Hybrid Retrieval

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

KnowFlow needs to support two search modes:

1. **Keyword search** — traditional full-text search matching specific terms, acronyms, and
   exact phrases. Must work even when the AI provider is completely down (Architecture Rule #7).
2. **Semantic search** — vector similarity search to find conceptually related content even when
   the exact words differ.

Both search modes must enforce document-level access control: a user must never see results
from documents they are not authorized to access (Architecture Rule #3). The search system
must also support the AI copilot's retrieval-augmented generation (RAG) pipeline.

We are already using PostgreSQL as the primary database.

## Decision

### Keyword Search: PostgreSQL tsvector + GIN Index

Use PostgreSQL's built-in full-text search capabilities:

```sql
-- On document_chunks table
ALTER TABLE document_chunks
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX idx_chunks_search_vector
    ON document_chunks USING GIN (search_vector);
```

Query pattern:

```sql
SELECT dc.id, dc.content, dc.page_number,
       ts_rank(dc.search_vector, query) AS rank
FROM document_chunks dc
JOIN documents d ON dc.document_id = d.id
CROSS JOIN plainto_tsquery('english', :searchTerms) AS query
WHERE dc.search_vector @@ query
  AND d.status = 'APPROVED'
  AND (
      d.access_level = 'PUBLIC'
      OR d.department_id IN (:userDepartmentIds)
      OR d.id IN (SELECT document_id FROM document_access WHERE user_id = :userId)
  )
ORDER BY rank DESC
LIMIT :pageSize OFFSET :offset;
```

### Semantic Search: pgvector with HNSW Index

Use the `pgvector` extension for vector similarity search:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE document_chunks
    ADD COLUMN embedding vector(1536);  -- dimension matches embedding model

CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
```

Query pattern:

```sql
SELECT dc.id, dc.content, dc.page_number,
       1 - (dc.embedding <=> :queryEmbedding) AS similarity
FROM document_chunks dc
JOIN documents d ON dc.document_id = d.id
WHERE dc.embedding IS NOT NULL
  AND d.status = 'APPROVED'
  AND (
      d.access_level = 'PUBLIC'
      OR d.department_id IN (:userDepartmentIds)
      OR d.id IN (SELECT document_id FROM document_access WHERE user_id = :userId)
  )
ORDER BY dc.embedding <=> :queryEmbedding
LIMIT :topK;
```

### Hybrid Retrieval: Reciprocal Rank Fusion (RRF)

For the copilot's RAG pipeline, combine keyword and semantic results using RRF:

```
RRF_score(d) = Σ  1 / (k + rank_in_list_i(d))
```

Where `k = 60` (standard constant). The algorithm:

1. Execute keyword search → get top N results with ranks.
2. Execute semantic search → get top N results with ranks.
3. For each unique chunk appearing in either list, compute RRF score.
4. Sort by RRF score descending.
5. Return top K results.

| Parameter | Default | Configurable |
|-----------|---------|-------------|
| Keyword search top-N | 20 | `knowflow.search.keyword.top-n` |
| Semantic search top-N | 20 | `knowflow.search.semantic.top-n` |
| RRF constant k | 60 | `knowflow.search.rrf.k` |
| Final top-K returned | 10 | `knowflow.search.results.top-k` |

### ACL Filtering

**Critical:** Access control is enforced **in the SQL query**, not as a post-filter.
This ensures:

- Users never see unauthorized content, even transiently.
- Pagination is correct (no "holes" from filtered results).
- The database optimizer can use indexes on the ACL predicates.

The ACL predicate is encapsulated in `DocumentAccessPolicy` and injected into all search
queries consistently.

### Index Tuning

| Index | Type | Parameters | Rationale |
|-------|------|-----------|-----------|
| `idx_chunks_search_vector` | GIN | Default | Standard for tsvector; fast for `@@` operator |
| `idx_chunks_embedding_hnsw` | HNSW | `m=16, ef_construction=200` | Good recall/speed tradeoff for < 100k vectors |
| `idx_documents_status` | B-tree | On `status` | Filter approved documents quickly |
| `idx_documents_department` | B-tree | On `department_id` | ACL filtering |
| `idx_document_access_user` | B-tree | On `(user_id, document_id)` | ACL subquery |

### Degradation Behavior

| AI Provider Status | Keyword Search | Semantic Search | Copilot |
|--------------------|---------------|-----------------|---------|
| Healthy | ✅ Works | ✅ Works | ✅ Works (hybrid) |
| Down | ✅ Works | ❌ Unavailable (no embeddings for query) | ❌ Returns "AI service temporarily unavailable" |
| `knowflow.ai.enabled=false` | ✅ Works | ❌ Disabled | ❌ Disabled |

Keyword search has **zero dependency** on the AI provider.

## Alternatives Considered

### Elasticsearch / OpenSearch

| Aspect | Assessment |
|--------|------------|
| Pros | Purpose-built for full-text search. Rich query DSL. Built-in analyzers for many languages. Proven at massive scale. |
| Cons | Separate infrastructure to deploy, monitor, and backup. ACL filtering must be reimplemented outside PostgreSQL transactions. Data consistency between PG and ES requires sync logic. Adds operational burden for a solo developer. |
| Verdict | **Rejected.** PostgreSQL's full-text search is sufficient for our scale (< 100k documents initially). Eliminates an entire infrastructure component. |

### Dedicated Vector Database (Pinecone, Weaviate, Qdrant, Milvus)

| Aspect | Assessment |
|--------|------------|
| Pros | Optimized for vector operations. Managed options available. Advanced filtering capabilities. |
| Cons | Separate ACL system needed — must replicate document permissions to the vector DB. Consistency lag between PG and vector DB. Additional cost (especially managed services). Additional operational complexity. |
| Verdict | **Rejected.** pgvector keeps vectors in the same database as ACLs, enabling transactional consistency and SQL-level access control in a single query. Performance is adequate for < 100k documents. |

### Keyword Search Only (No Vectors)

| Aspect | Assessment |
|--------|------------|
| Pros | Simpler. No embedding model dependency. |
| Cons | Users can't find conceptually related content when they don't know the exact terms. Significantly weaker RAG quality for the copilot. |
| Verdict | **Rejected.** Semantic search is a core value proposition of KnowFlow. |

## Consequences

### Positive

- **Single database:** All data (metadata, content, vectors, ACLs) in PostgreSQL. One backup
  strategy, one consistency model, one connection pool.
- **Transactional ACLs:** Access control predicates are part of the search query — impossible
  to accidentally bypass.
- **Graceful degradation:** Keyword search works regardless of AI provider health.
- **Operational simplicity:** No additional infrastructure to deploy or monitor.

### Negative

- **Scale ceiling:** pgvector HNSW performance degrades at very large vector counts
  (> 1M vectors). ACL-filtered scans reduce the effective index utilization.
- **Single-node bottleneck:** PostgreSQL is a single point of failure for all search operations.
  Mitigated by read replicas if needed.
- **Limited text analysis:** PostgreSQL's full-text search has fewer analyzers and less
  sophisticated relevance tuning than Elasticsearch.

### Re-evaluation Triggers

Re-evaluate this decision if **any** of the following occur:

| Trigger | Threshold | Alternative to Consider |
|---------|-----------|------------------------|
| Keyword search p95 latency | > 500ms at production load | Elasticsearch |
| Semantic search p95 latency | > 1s at production load | Dedicated vector DB |
| Document count | > 100k documents | Elasticsearch + vector DB |
| Vector count | > 1M vectors | Dedicated vector DB with HNSW sharding |

### Obligations

- [ ] Add `pgvector` extension to Docker Compose and Flyway baseline migration.
- [ ] Create `SearchService` with `keywordSearch()`, `semanticSearch()`, and `hybridSearch()`
      methods.
- [ ] Write integration tests (Testcontainers with pgvector) verifying ACL filtering for all
      search modes.
- [ ] Add query explain plans to performance test suite.
- [ ] Monitor search latency percentiles in production.

---

*Cross-references:* [ADR-005](ADR-005-chunking-embedding.md) (chunking and embedding strategy),
[ADR-006](ADR-006-llm-provider.md) (AI provider and fallback),
[ADR-001](ADR-001-modular-monolith-architecture.md) (search module boundaries)
