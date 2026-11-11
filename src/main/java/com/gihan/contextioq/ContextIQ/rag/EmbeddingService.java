package com.gihan.contextioq.ContextIQ.rag;

import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmbeddingService {
    private final EmbeddingModel embedding;
    private final String model;

    public EmbeddingService(EmbeddingModel embedding, @Value("${contextiq.rag.embedding-model}") String model) {
        this.embedding = embedding;
        this.model = model;
    }

    public String model() { return model; }

    public List<float[]> documents(List<String> chunks) {
        return embed(chunks.stream().map(s -> "search_document: " + s).toList());
    }

    public float[] query(String text) { return embed(List.of("search_query: " + text)).getFirst(); }

    private List<float[]> embed(List<String> texts) {
        try {
            var vectors = embedding.embed(texts);
            if (vectors.size() != texts.size()) throw new IllegalStateException("Embedding count mismatch");
            for (var vector : vectors) {
                if (vector.length != 768) throw new IllegalStateException("Expected 768 dimensions");
                double norm = 0;
                for (float value : vector) {
                    if (!Float.isFinite(value)) throw new IllegalStateException("Non-finite embedding");
                    norm += value * (double) value;
                }
                if (norm == 0) throw new IllegalStateException("Zero embedding");
            }
            return vectors;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Embedding failed. Check Ollama and the installed nomic-embed-text model (768 dimensions). No document was indexed.");
        }
    }

    public static String vectorLiteral(float[] vector) {
        return java.util.Arrays.toString(vector);
    }
}
