ALTER TABLE messages ADD COLUMN mode VARCHAR(20);
ALTER TABLE messages ADD COLUMN model VARCHAR(120);
ALTER TABLE messages ADD COLUMN input_tokens INTEGER;
ALTER TABLE messages ADD COLUMN output_tokens INTEGER;
-- Preserve the provenance of earlier assistant messages when switching providers.
UPDATE messages SET mode = 'MOCK' WHERE role = 'ASSISTANT';
