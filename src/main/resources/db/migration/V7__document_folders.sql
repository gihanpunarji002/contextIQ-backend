CREATE TABLE document_folders (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES app_users(id),
 name VARCHAR(80) NOT NULL,
 icon VARCHAR(24) NOT NULL,
 color VARCHAR(16) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(id, owner_id),
 UNIQUE(owner_id, name)
);
ALTER TABLE documents ADD COLUMN folder_id UUID;
ALTER TABLE documents ADD CONSTRAINT fk_documents_owned_folder
 FOREIGN KEY(folder_id, owner_id) REFERENCES document_folders(id, owner_id);
CREATE INDEX idx_documents_folder ON documents(owner_id, folder_id);
CREATE TABLE conversation_folders (
 conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
 folder_id UUID NOT NULL,
 name VARCHAR(80) NOT NULL,
 selection_index INTEGER NOT NULL,
 PRIMARY KEY(conversation_id, folder_id)
);
