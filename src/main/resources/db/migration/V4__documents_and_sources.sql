CREATE TABLE documents (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES app_users(id),
 filename VARCHAR(180) NOT NULL,
 size_bytes BIGINT NOT NULL,
 chunk_count INTEGER NOT NULL,
 embedding_model VARCHAR(120) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_documents_owner ON documents(owner_id, created_at DESC);
CREATE TABLE document_chunks (
 id UUID PRIMARY KEY,
 document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 chunk_index INTEGER NOT NULL,
 content TEXT NOT NULL,
 UNIQUE(document_id, chunk_index)
);
ALTER TABLE messages ADD COLUMN retrieval_used BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE message_sources (
 message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
 source_index INTEGER NOT NULL,
 document_id UUID NOT NULL,
 chunk_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL,
 chunk_index INTEGER NOT NULL,
 content TEXT NOT NULL,
 score DOUBLE PRECISION NOT NULL,
 PRIMARY KEY(message_id, source_index)
);
