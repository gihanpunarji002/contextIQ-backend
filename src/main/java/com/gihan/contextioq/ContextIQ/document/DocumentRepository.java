package com.gihan.contextioq.ContextIQ.document;

import com.gihan.contextioq.ContextIQ.rag.EmbeddingService;
import com.gihan.contextioq.ContextIQ.rag.Source;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {
    private final JdbcTemplate jdbc;
    private final String vectorType;

    public DocumentRepository(JdbcTemplate jdbc, @Value("${contextiq.rag.vector-type:vector}") String vectorType) {
        if (!Set.of("vector", "text").contains(vectorType)) throw new IllegalArgumentException("Invalid vector storage type");
        this.jdbc = jdbc;
        this.vectorType = vectorType;
    }

    public List<DocumentView> list(UUID owner) {
        return jdbc.query("SELECT * FROM documents WHERE owner_id = ? ORDER BY created_at DESC, id",
                (rs, row) -> new DocumentView(rs.getObject("id", UUID.class), rs.getString("filename"),
                        rs.getLong("size_bytes"), rs.getInt("chunk_count"), rs.getString("embedding_model"),
                        rs.getTimestamp("created_at").toInstant(), rs.getObject("folder_id", UUID.class)), owner);
    }

    public DocumentView insert(UUID owner, String name, long size, String model,
                               List<String> chunks, List<float[]> vectors) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbc.update("INSERT INTO documents (id, owner_id, filename, size_bytes, chunk_count, embedding_model, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, owner, name, size, chunks.size(), model, Timestamp.from(now));
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?, CAST(? AS " + vectorType + "))",
                    UUID.randomUUID(), id, i, chunks.get(i), EmbeddingService.vectorLiteral(vectors.get(i)));
        }
        return new DocumentView(id, name, size, chunks.size(), model, now);
    }

    public boolean delete(UUID id, UUID owner) {
        return jdbc.update("DELETE FROM documents WHERE id = ? AND owner_id = ?", id, owner) == 1;
    }

    public List<Source> search(UUID owner, String model, float[] vector, double threshold) {
        return search(owner, model, vector, threshold, null);
    }

    public List<Source> search(UUID owner, String model, float[] vector, double threshold, List<UUID> selected) {
        return search(owner, model, vector, threshold, selected, List.of());
    }

    public List<Source> search(UUID owner, String model, float[] vector, double threshold, List<UUID> selected, List<UUID> folders) {
        if (selected != null && selected.isEmpty() && folders.isEmpty()) return List.of();
        var predicates = new ArrayList<String>();
        if (selected != null && !selected.isEmpty()) predicates.add("d.id IN (" + String.join(",", Collections.nCopies(selected.size(), "?")) + ")");
        if (!folders.isEmpty()) predicates.add("d.folder_id IN (" + String.join(",", Collections.nCopies(folders.size(), "?")) + ")");
        String scope = predicates.isEmpty() ? "" : " AND (" + String.join(" OR ", predicates) + ")";
        var args = new ArrayList<Object>(List.of(EmbeddingService.vectorLiteral(vector), owner, model, EmbeddingService.vectorLiteral(vector), threshold));
        if (selected != null) args.addAll(selected);
        args.addAll(folders);
        // Ownership is part of the SQL predicate, before ranking/limiting. Never search other accounts.
        return jdbc.query("""
                SELECT d.id AS document_id, c.id AS chunk_id, d.filename, c.chunk_index, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS score
                FROM document_chunks c JOIN documents d ON d.id = c.document_id
                WHERE d.owner_id = ? AND d.embedding_model = ?
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                """ + scope + " ORDER BY score DESC, c.id LIMIT 4", (rs, row) -> new Source(rs.getObject("document_id", UUID.class),
                rs.getObject("chunk_id", UUID.class), rs.getString("filename"), rs.getInt("chunk_index"),
                rs.getString("content"), rs.getDouble("score")),
                args.toArray());
    }

    public void saveSources(UUID message, List<Source> sources) {
        for (int i = 0; i < sources.size(); i++) {
            var s = sources.get(i);
            jdbc.update("INSERT INTO message_sources VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    message, i, s.documentId(), s.chunkId(), s.filename(), s.chunkIndex(), s.content(), s.score());
        }
    }

    // Called only after ChatService has checked conversation ownership.
    public List<Source> sources(UUID message) {
        return jdbc.query("SELECT * FROM message_sources WHERE message_id = ? ORDER BY source_index",
                (rs, row) -> new Source(rs.getObject("document_id", UUID.class), rs.getObject("chunk_id", UUID.class),
                        rs.getString("filename"), rs.getInt("chunk_index"), rs.getString("content"), rs.getDouble("score")), message);
    }
}
