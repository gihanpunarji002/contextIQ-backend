package com.gihan.contextioq.ContextIQ.rag;

import java.util.UUID;

/** A snapshot of a retrieved passage, retained with the answer for auditability. */
public record Source(UUID documentId, UUID chunkId, String filename, int chunkIndex,
                     String content, double score) {}
