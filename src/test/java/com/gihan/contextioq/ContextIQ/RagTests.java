package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.gihan.contextioq.ContextIQ.document.*;
import com.gihan.contextioq.ContextIQ.rag.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.server.ResponseStatusException;

class RagTests {
    @Test void chunksCoverTextWithOverlapWithoutBreakingUnicode() {
        var chunker = new TextChunker();
        assertTrue(chunker.split("  \n").isEmpty());
        String text = "😀".repeat(2500);
        var chunks = chunker.split(text);
        assertEquals(3, chunks.size());
        assertEquals(1200, chunks.getFirst().codePointCount(0, chunks.getFirst().length()));
        assertEquals(text, chunks.get(0) + chunks.get(1).substring(400) + chunks.get(2).substring(400));
    }

    @Test void retrievalPassesOwnerAndThresholdAndSkipsEmbeddingForEmptyLibrary() {
        var documents = mock(DocumentRepository.class);
        var embeddings = mock(EmbeddingService.class);
        var retrieval = new RetrievalService(embeddings, documents, 0.45);
        var owner = UUID.randomUUID();
        assertTrue(retrieval.retrieve(owner, "question").isEmpty());
        verifyNoInteractions(embeddings);
        when(documents.list(owner)).thenReturn(List.of(new DocumentView(UUID.randomUUID(), "a.txt", 12, 1, "nomic", java.time.Instant.now())));
        when(embeddings.model()).thenReturn("nomic");
        float[] vector = new float[]{1};
        when(embeddings.query("question")).thenReturn(vector);
        retrieval.retrieve(owner, "question");
        verify(documents).search(owner, "nomic", vector, 0.45);
    }

    @Test void queryPrefixAndInvalidDimensions() {
        var model = mock(EmbeddingModel.class);
        var service = new EmbeddingService(model, "nomic-embed-text:latest");
        when(model.embed(anyList())).thenReturn(List.of(new float[12]));
        assertThrows(ResponseStatusException.class, () -> service.query("when?"));
        verify(model).embed(List.of("search_query: when?"));
        float[] valid = new float[768]; valid[0] = 1;
        when(model.embed(anyList())).thenReturn(List.of(valid));
        assertEquals(768, service.query("when?").length);
    }
}
