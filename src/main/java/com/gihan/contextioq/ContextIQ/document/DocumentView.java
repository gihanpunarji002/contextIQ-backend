package com.gihan.contextioq.ContextIQ.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentView(UUID id, String filename, long sizeBytes, int chunkCount, String embeddingModel,
                           Instant createdAt, UUID folderId, UUID workspaceId, UUID createdBy) {
    public DocumentView(UUID id, String filename, long sizeBytes, int chunkCount, String model, Instant at, UUID folderId) { this(id, filename, sizeBytes, chunkCount, model, at, folderId, null, null); }
    public DocumentView(UUID id, String filename, long sizeBytes, int chunkCount, String embeddingModel, Instant createdAt) {
        this(id, filename, sizeBytes, chunkCount, embeddingModel, createdAt, null);
    }
}
