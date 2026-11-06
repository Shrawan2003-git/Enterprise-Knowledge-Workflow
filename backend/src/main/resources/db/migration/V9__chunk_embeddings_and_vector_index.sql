-- V9__chunk_embeddings_and_vector_index.sql: Vector embeddings storage and HNSW cosine index

CREATE TABLE IF NOT EXISTS chunk_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id UUID NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    embedding vector(1536),
    embedding_model VARCHAR(100) NOT NULL,
    dimensions INTEGER NOT NULL,
    pipeline_version VARCHAR(50) NOT NULL DEFAULT 'v1',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chunk_embedding UNIQUE (chunk_id, embedding_model, pipeline_version)
);

CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_hnsw ON chunk_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_chunk_id ON chunk_embeddings(chunk_id);
