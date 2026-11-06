-- V8__ingestion_chunks_and_job_enhancements.sql: Chunk storage and job resilience

-- 1. Enhance ingestion_jobs with classification and audit columns
ALTER TABLE ingestion_jobs 
ADD COLUMN IF NOT EXISTS error_message VARCHAR(500),
ADD COLUMN IF NOT EXISTS error_type VARCHAR(30),
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 2. Enhance document_versions with active pipeline version and safe error code
ALTER TABLE document_versions
ADD COLUMN IF NOT EXISTS error_code VARCHAR(100),
ADD COLUMN IF NOT EXISTS active_pipeline_version VARCHAR(50) DEFAULT 'v1';

-- 3. Create document_chunks table
CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id UUID NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    pipeline_version VARCHAR(50) NOT NULL DEFAULT 'v1',
    chunk_index INTEGER NOT NULL,
    text TEXT NOT NULL,
    page_start INTEGER NOT NULL,
    page_end INTEGER NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    content_hash VARCHAR(64) NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', text)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chunk_version_pipeline_idx UNIQUE (version_id, pipeline_version, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_chunks_version_pipeline ON document_chunks(version_id, pipeline_version);
CREATE INDEX IF NOT EXISTS idx_chunks_doc_id ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_tsv ON document_chunks USING GIN(tsv);
