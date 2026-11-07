CREATE TABLE app_users (
 id UUID PRIMARY KEY,
 first_name VARCHAR(80) NOT NULL,
 last_name VARCHAR(80) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(255),
 google_subject VARCHAR(255) UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT account_has_identity CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL)
);
-- Retain anonymous Phase 0 records without exposing them to newly registered users.
ALTER TABLE conversations ADD COLUMN owner_id UUID REFERENCES app_users(id);
CREATE INDEX idx_conversation_owner_updated ON conversations(owner_id, updated_at DESC, id);
