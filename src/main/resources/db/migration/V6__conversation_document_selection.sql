ALTER TABLE conversations ADD COLUMN use_documents BOOLEAN NOT NULL DEFAULT FALSE;
-- No document FK: retain the name and ID when a selected document is deleted.
CREATE TABLE conversation_documents (
 conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 document_id UUID NOT NULL,
 filename VARCHAR(180) NOT NULL,
 selection_index INTEGER NOT NULL,
 PRIMARY KEY (conversation_id, document_id)
);
