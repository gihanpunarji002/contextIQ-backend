CREATE TABLE conversations (
 id UUID PRIMARY KEY,
 title VARCHAR(120) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE messages (
 id UUID PRIMARY KEY,
 conversation_id UUID NOT NULL REFERENCES conversations(id),
 role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
 content TEXT NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_conversations_updated ON conversations(updated_at DESC, id);
CREATE INDEX idx_messages_history ON messages(conversation_id, created_at, id);
