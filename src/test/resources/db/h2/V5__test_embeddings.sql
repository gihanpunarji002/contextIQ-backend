-- H2 verifies relational behavior only, not pgvector operators or retrieval ranking.
ALTER TABLE document_chunks ADD COLUMN embedding TEXT NOT NULL;
