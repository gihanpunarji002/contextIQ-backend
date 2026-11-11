package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import com.gihan.contextioq.ContextIQ.config.EmbeddingConfig;
import com.gihan.contextioq.ContextIQ.config.OllamaConfig;
import com.gihan.contextioq.ContextIQ.ai.service.OllamaAiChatService;
import com.gihan.contextioq.ContextIQ.rag.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in: uses already-running Ollama only. No application server, database, or model is started. */
@EnabledIfEnvironmentVariable(named = "CONTEXTIQ_RAG_SMOKE", matches = "true")
class RagLiveTests {
    @Test void localNomicEmbedsAndGemmaAnswersFromEvidence() {
        var embedding = new EmbeddingService(new EmbeddingConfig().embeddingModel(
                "http://localhost:11434", "nomic-embed-text:latest"), "nomic-embed-text:latest");
        String passage = "Project Juniper's official launch date is May 14. The launch owner is Morgan.";
        String question = "What is the official launch date of Project Juniper?";
        var vectors = embedding.documents(List.of(passage, "The cafeteria serves vegetable soup every Friday."));
        var query = embedding.query(question);
        assertEquals(768, query.length);
        assertTrue(cosine(query, vectors.get(0)) > cosine(query, vectors.get(1)), "Relevant passage should rank above unrelated text");
        var source = new Source(UUID.randomUUID(), UUID.randomUUID(), "launch.txt", 0, passage, cosine(query, vectors.get(0)));
        var chat = new OllamaAiChatService(new OllamaConfig().ollamaChatModel(
                "http://localhost:11434", "gemma4:e2b", 256, 4096), "gemma4:e2b");
        var answer = chat.groundedReply(question, List.of(source));
        assertEquals("OLLAMA", answer.mode());
        assertTrue(answer.content().contains("14"), "Answer should use the supplied launch date");
        assertTrue(answer.content().contains("[1]"), "Answer should cite the supplied source");
        System.out.println("RAG local-model smoke passed: 768-dimensional embeddings, relevant passage ranked first, grounded answer cited [1].");
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0, x = 0, y = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; x += a[i] * a[i]; y += b[i] * b[i]; }
        return dot / Math.sqrt(x * y);
    }
}
