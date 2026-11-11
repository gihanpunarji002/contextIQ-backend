CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE document_chunks ADD COLUMN embedding vector(768) NOT NULL;
-- Exact cosine search is deliberate for this small, owner-filtered library.
-- Add an approximate index only after measuring recall with ownership filters.
