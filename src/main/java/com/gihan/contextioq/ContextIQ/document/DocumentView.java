package com.gihan.contextioq.ContextIQ.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentView(UUID id, String filename, long sizeBytes, int chunkCount,
                           String embeddingModel, Instant createdAt) {}
