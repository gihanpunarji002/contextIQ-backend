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
    private final com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess access;
    private final String vectorType;

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentRepository(com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess access, JdbcTemplate jdbc, @Value("${contextiq.rag.vector-type:vector}") String vectorType) {
        if (!Set.of("vector", "text").contains(vectorType)) throw new IllegalArgumentException("Invalid vector storage type");
        this.access = access;
        this.jdbc = jdbc;
        this.vectorType = vectorType;
    }

    public DocumentRepository(JdbcTemplate jdbc, String vectorType) { this(null, jdbc, vectorType); }

    public List<DocumentView> list(UUID owner) {
        return jdbc.query("SELECT * FROM documents WHERE workspace_id = ? ORDER BY created_at DESC, id",
                (rs, row) -> new DocumentView(rs.getObject("id", UUID.class), rs.getString("filename"),
                        rs.getLong("size_bytes"), rs.getInt("chunk_count"), rs.getString("embedding_model"),
                        rs.getTimestamp("created_at").toInstant(), rs.getObject("folder_id", UUID.class), rs.getObject("workspace_id", UUID.class), rs.getObject("owner_id", UUID.class)), owner);
    }

    public DocumentView insert(UUID owner, String name, long size, String model,
                               List<String> chunks, List<float[]> vectors) {
        access.ensurePersonal(owner);
        return insert(access.require(owner, owner), name, size, model, chunks, vectors);
    }

    public DocumentView insert(com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess.Scope scope, String name, long size, String model, List<String> chunks, List<float[]> vectors) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbc.update("INSERT INTO workspace_resources VALUES (?, ?, ?)",id,scope.workspaceId(),scope.organizationId());
        jdbc.update("INSERT INTO documents (id, owner_id, filename, size_bytes, chunk_count, embedding_model, created_at, workspace_id, organization_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, scope.userId(), name, size, chunks.size(), model, Timestamp.from(now), scope.workspaceId(), scope.organizationId());
        for (int i = 0; i < chunks.size(); i++) {
            jdbc.update("INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?, CAST(? AS " + vectorType + "))",
                    UUID.randomUUID(), id, i, chunks.get(i), EmbeddingService.vectorLiteral(vectors.get(i)));
        }
        return new DocumentView(id, name, size, chunks.size(), model, now, null, scope.workspaceId(), scope.userId());
    }

    public boolean delete(UUID id, UUID owner) {
        return jdbc.update("DELETE FROM documents WHERE id = ? AND workspace_id = ?", id, owner) == 1;
    }

    public List<Source> search(UUID owner, String model, float[] vector, double threshold) {
        return search(owner, model, vector, threshold, null);
    }

    public List<Source> search(UUID owner, String model, float[] vector, double threshold, List<UUID> selected) {
        return search(owner, model, vector, threshold, selected, List.of());
    }

    public List<Source> search(UUID owner, String model, float[] vector, double threshold, List<UUID> selected, List<UUID> folders) {
        return searchScoped(owner, owner, model, vector, threshold, selected, folders);
    }

    public List<Source> searchScoped(UUID workspace, UUID user, String model, float[] vector, double threshold, List<UUID> selected, List<UUID> folders) {
        if (selected != null && selected.isEmpty() && folders.isEmpty()) return List.of();
        var predicates = new ArrayList<String>();
        if (selected != null && !selected.isEmpty()) predicates.add("d.id IN (" + String.join(",", Collections.nCopies(selected.size(), "?")) + ")");
        if (!folders.isEmpty()) predicates.add("d.folder_id IN (" + String.join(",", Collections.nCopies(folders.size(), "?")) + ")");
        String scope = predicates.isEmpty() ? "" : " AND (" + String.join(" OR ", predicates) + ")";
        var args = new ArrayList<Object>(List.of(EmbeddingService.vectorLiteral(vector), workspace, user, model, EmbeddingService.vectorLiteral(vector), threshold));
        if (selected != null) args.addAll(selected);
        args.addAll(folders);
        // Ownership is part of the SQL predicate, before ranking/limiting. Never search other accounts.
        return jdbc.query("""
                SELECT d.id AS document_id, c.id AS chunk_id, d.filename, c.chunk_index, c.content,
                       1 - (c.embedding <=> CAST(? AS vector)) AS score
                FROM document_chunks c JOIN documents d ON d.id = c.document_id
                WHERE d.workspace_id = ? AND EXISTS (
                  SELECT 1 FROM workspace_members wm JOIN organization_members om ON om.organization_id=wm.organization_id AND om.user_id=wm.user_id
                  JOIN workspaces w ON w.id=wm.workspace_id AND w.organization_id=wm.organization_id
                  WHERE wm.workspace_id=d.workspace_id AND wm.organization_id=d.organization_id AND wm.user_id=?
                  AND wm.active=TRUE AND om.active=TRUE AND w.deleted=FALSE
                ) AND d.embedding_model = ?
                  AND 1 - (c.embedding <=> CAST(? AS vector)) >= ?
                """ + scope + " ORDER BY score DESC, c.id LIMIT 4", (rs, row) -> new Source(rs.getObject("document_id", UUID.class),
                rs.getObject("chunk_id", UUID.class), rs.getString("filename"), rs.getInt("chunk_index"),
                rs.getString("content"), rs.getDouble("score")),
                args.toArray());
    }

    public void saveSources(UUID message, List<Source> sources) {
        for (int i = 0; i < sources.size(); i++) {
            var s = sources.get(i);
            jdbc.update("INSERT INTO message_sources SELECT ?, ?, ?, ?, ?, ?, ?, ?, workspace_id, organization_id FROM messages WHERE id=?",
                    message, i, s.documentId(), s.chunkId(), s.filename(), s.chunkIndex(), s.content(), s.score(), message);
        }
    }

    // Called only after ChatService has checked conversation ownership.
    public List<Source> sources(UUID message) {
        return jdbc.query("SELECT * FROM message_sources WHERE message_id = ? ORDER BY source_index",
                (rs, row) -> new Source(rs.getObject("document_id", UUID.class), rs.getObject("chunk_id", UUID.class),
                        rs.getString("filename"), rs.getInt("chunk_index"), rs.getString("content"), rs.getDouble("score")), message);
    }
}
