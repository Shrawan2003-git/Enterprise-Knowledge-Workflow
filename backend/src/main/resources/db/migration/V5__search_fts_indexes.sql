-- V5__search_fts_indexes.sql: tsvector columns and GIN indexes for PostgreSQL Full-Text Search

ALTER TABLE documents ADD COLUMN IF NOT EXISTS tsv_content tsvector;

-- Function to generate combined tsvector from title and description
CREATE OR REPLACE FUNCTION documents_tsv_trigger() RETURNS trigger AS $$
BEGIN
  NEW.tsv_content := 
    setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
  RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_documents_tsv ON documents;
CREATE TRIGGER trg_documents_tsv
BEFORE INSERT OR UPDATE OF title, description ON documents
FOR EACH ROW EXECUTE FUNCTION documents_tsv_trigger();

-- Backfill existing rows
UPDATE documents SET tsv_content = 
  setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
  setweight(to_tsvector('english', coalesce(description, '')), 'B');

CREATE INDEX IF NOT EXISTS idx_documents_tsv_content ON documents USING gin(tsv_content);
