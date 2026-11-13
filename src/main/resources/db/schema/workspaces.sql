CREATE TABLE organizations (
 id UUID PRIMARY KEY, name VARCHAR(80) NOT NULL, created_by UUID REFERENCES app_users(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE organization_members (
 organization_id UUID NOT NULL REFERENCES organizations(id), user_id UUID NOT NULL REFERENCES app_users(id),
 role VARCHAR(10) NOT NULL CHECK(role IN ('ADMIN','MEMBER')), active BOOLEAN NOT NULL DEFAULT TRUE,
 PRIMARY KEY(organization_id,user_id), UNIQUE(user_id,organization_id)
);
CREATE TABLE workspaces (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), name VARCHAR(80) NOT NULL,
 description VARCHAR(500) NOT NULL, created_by UUID REFERENCES app_users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 personal BOOLEAN NOT NULL DEFAULT FALSE, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(id,organization_id), UNIQUE(organization_id,name)
);
CREATE TABLE workspace_members (
 workspace_id UUID NOT NULL, organization_id UUID NOT NULL, user_id UUID NOT NULL,
 role VARCHAR(10) NOT NULL CHECK(role IN ('ADMIN','EDITOR','VIEWER')), active BOOLEAN NOT NULL DEFAULT TRUE,
 access_version UUID NOT NULL,
 PRIMARY KEY(workspace_id,user_id),
 FOREIGN KEY(workspace_id,organization_id) REFERENCES workspaces(id,organization_id),
 FOREIGN KEY(user_id,organization_id) REFERENCES organization_members(user_id,organization_id)
);
CREATE INDEX idx_workspace_members_user ON workspace_members(user_id,active,workspace_id);
INSERT INTO organizations SELECT id,'Personal organization',id,created_at FROM app_users;
INSERT INTO organization_members SELECT id,id,'ADMIN',TRUE FROM app_users;
INSERT INTO workspaces SELECT id,id,'Personal','Your private knowledge workspace.',id,created_at,TRUE,FALSE FROM app_users;
INSERT INTO workspace_members SELECT id,id,id,'ADMIN',TRUE,id FROM app_users;
INSERT INTO organizations VALUES ('00000000-0000-0000-0000-000000000008','Legacy records',NULL,CURRENT_TIMESTAMP);
INSERT INTO workspaces VALUES ('00000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000008','Legacy','Unclaimed anonymous records',NULL,CURRENT_TIMESTAMP,FALSE,TRUE);
ALTER TABLE workspaces ADD CONSTRAINT fk_workspace_creator FOREIGN KEY(created_by,organization_id) REFERENCES organization_members(user_id,organization_id);
ALTER TABLE documents ADD COLUMN workspace_id UUID;
ALTER TABLE documents ADD COLUMN organization_id UUID;
UPDATE documents SET workspace_id=owner_id,organization_id=owner_id;
ALTER TABLE documents ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE documents ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE document_folders ADD COLUMN workspace_id UUID;
ALTER TABLE document_folders ADD COLUMN organization_id UUID;
ALTER TABLE document_folders ADD COLUMN parent_id UUID;
UPDATE document_folders SET workspace_id=owner_id,organization_id=owner_id;
ALTER TABLE document_folders ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE document_folders ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE conversations ADD COLUMN workspace_id UUID;
ALTER TABLE conversations ADD COLUMN organization_id UUID;
UPDATE conversations SET workspace_id=COALESCE(owner_id,'00000000-0000-0000-0000-000000000008'),organization_id=COALESCE(owner_id,'00000000-0000-0000-0000-000000000008');
ALTER TABLE conversations ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE conversations ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE messages ADD COLUMN workspace_id UUID;
ALTER TABLE messages ADD COLUMN organization_id UUID;
UPDATE messages SET workspace_id=(SELECT workspace_id FROM conversations c WHERE c.id=conversation_id),organization_id=(SELECT organization_id FROM conversations c WHERE c.id=conversation_id);
ALTER TABLE messages ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE messages ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE documents DROP CONSTRAINT fk_documents_owned_folder;
ALTER TABLE document_folders ADD CONSTRAINT uq_folder_tenant UNIQUE(id,workspace_id,organization_id);
ALTER TABLE document_folders ADD CONSTRAINT uq_folder_workspace_name UNIQUE(workspace_id,name);
ALTER TABLE documents ADD CONSTRAINT uq_document_tenant UNIQUE(id,workspace_id,organization_id);
ALTER TABLE conversations ADD CONSTRAINT uq_conversation_tenant UNIQUE(id,workspace_id,organization_id);
ALTER TABLE messages ADD CONSTRAINT uq_message_tenant UNIQUE(id,workspace_id,organization_id);
ALTER TABLE document_folders ADD CONSTRAINT fk_folder_workspace FOREIGN KEY(workspace_id,organization_id) REFERENCES workspaces(id,organization_id);
ALTER TABLE documents ADD CONSTRAINT fk_document_workspace FOREIGN KEY(workspace_id,organization_id) REFERENCES workspaces(id,organization_id);
ALTER TABLE conversations ADD CONSTRAINT fk_conversation_workspace FOREIGN KEY(workspace_id,organization_id) REFERENCES workspaces(id,organization_id);
ALTER TABLE messages ADD CONSTRAINT fk_message_conversation_tenant FOREIGN KEY(conversation_id,workspace_id,organization_id) REFERENCES conversations(id,workspace_id,organization_id);
ALTER TABLE document_folders ADD CONSTRAINT fk_folder_parent_tenant FOREIGN KEY(parent_id,workspace_id,organization_id) REFERENCES document_folders(id,workspace_id,organization_id);
ALTER TABLE document_folders ADD CONSTRAINT ck_folder_not_self CHECK(parent_id IS NULL OR parent_id <> id);
ALTER TABLE documents ADD CONSTRAINT fk_document_folder_tenant FOREIGN KEY(folder_id,workspace_id,organization_id) REFERENCES document_folders(id,workspace_id,organization_id);
ALTER TABLE documents ADD CONSTRAINT fk_document_creator FOREIGN KEY(owner_id,organization_id) REFERENCES organization_members(user_id,organization_id);
ALTER TABLE document_folders ADD CONSTRAINT fk_folder_creator FOREIGN KEY(owner_id,organization_id) REFERENCES organization_members(user_id,organization_id);
ALTER TABLE conversations ADD CONSTRAINT fk_conversation_creator FOREIGN KEY(owner_id,organization_id) REFERENCES organization_members(user_id,organization_id);
CREATE INDEX idx_documents_workspace ON documents(workspace_id,created_at);
CREATE INDEX idx_folders_workspace_parent ON document_folders(workspace_id,parent_id);
CREATE INDEX idx_conversations_workspace_creator ON conversations(workspace_id,owner_id,updated_at);
CREATE TABLE workspace_resources (
 id UUID PRIMARY KEY, workspace_id UUID NOT NULL, organization_id UUID NOT NULL,
 UNIQUE(id,workspace_id,organization_id),
 FOREIGN KEY(workspace_id,organization_id) REFERENCES workspaces(id,organization_id)
);
INSERT INTO workspace_resources
 SELECT id,workspace_id,organization_id FROM documents
 UNION SELECT id,workspace_id,organization_id FROM document_folders
 UNION SELECT s.document_id,c.workspace_id,c.organization_id FROM conversation_documents s JOIN conversations c ON c.id=s.conversation_id
 UNION SELECT s.folder_id,c.workspace_id,c.organization_id FROM conversation_folders s JOIN conversations c ON c.id=s.conversation_id
 UNION SELECT s.document_id,m.workspace_id,m.organization_id FROM message_sources s JOIN messages m ON m.id=s.message_id;
ALTER TABLE documents ADD CONSTRAINT fk_document_registry FOREIGN KEY(id,workspace_id,organization_id) REFERENCES workspace_resources(id,workspace_id,organization_id);
ALTER TABLE document_folders ADD CONSTRAINT fk_folder_registry FOREIGN KEY(id,workspace_id,organization_id) REFERENCES workspace_resources(id,workspace_id,organization_id);
ALTER TABLE conversation_documents ADD COLUMN workspace_id UUID;
ALTER TABLE conversation_documents ADD COLUMN organization_id UUID;
UPDATE conversation_documents SET workspace_id=(SELECT workspace_id FROM conversations c WHERE c.id=conversation_id),organization_id=(SELECT organization_id FROM conversations c WHERE c.id=conversation_id);
ALTER TABLE conversation_documents ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE conversation_documents ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE conversation_documents ADD CONSTRAINT fk_selected_conversation FOREIGN KEY(conversation_id,workspace_id,organization_id) REFERENCES conversations(id,workspace_id,organization_id);
ALTER TABLE conversation_documents ADD CONSTRAINT fk_selected_document FOREIGN KEY(document_id,workspace_id,organization_id) REFERENCES workspace_resources(id,workspace_id,organization_id);
ALTER TABLE conversation_folders ADD COLUMN workspace_id UUID;
ALTER TABLE conversation_folders ADD COLUMN organization_id UUID;
UPDATE conversation_folders SET workspace_id=(SELECT workspace_id FROM conversations c WHERE c.id=conversation_id),organization_id=(SELECT organization_id FROM conversations c WHERE c.id=conversation_id);
ALTER TABLE conversation_folders ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE conversation_folders ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE conversation_folders ADD CONSTRAINT fk_selected_folder_conversation FOREIGN KEY(conversation_id,workspace_id,organization_id) REFERENCES conversations(id,workspace_id,organization_id);
ALTER TABLE conversation_folders ADD CONSTRAINT fk_selected_folder FOREIGN KEY(folder_id,workspace_id,organization_id) REFERENCES workspace_resources(id,workspace_id,organization_id);
ALTER TABLE message_sources ADD COLUMN workspace_id UUID;
ALTER TABLE message_sources ADD COLUMN organization_id UUID;
UPDATE message_sources SET workspace_id=(SELECT workspace_id FROM messages m WHERE m.id=message_id),organization_id=(SELECT organization_id FROM messages m WHERE m.id=message_id);
ALTER TABLE message_sources ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE message_sources ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE message_sources ADD CONSTRAINT fk_source_message FOREIGN KEY(message_id,workspace_id,organization_id) REFERENCES messages(id,workspace_id,organization_id);
ALTER TABLE message_sources ADD CONSTRAINT fk_source_document FOREIGN KEY(document_id,workspace_id,organization_id) REFERENCES workspace_resources(id,workspace_id,organization_id);
