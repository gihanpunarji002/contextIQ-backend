package com.gihan.contextioq.ContextIQ.chat.repository;

import com.gihan.contextioq.ContextIQ.chat.dto.ChatDtos.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Called inside ChatService transactions after checking conversation ownership. */
@Repository
public class ConversationDocuments {
    private final JdbcTemplate jdbc;
    public ConversationDocuments(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DocumentContext get(UUID conversation, UUID owner) {
        if (conversation == null) return new DocumentContext(false, List.of());
        boolean enabled = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT use_documents FROM conversations WHERE id = ? AND owner_id = ?", Boolean.class, conversation, owner));
        var selected = jdbc.query("""
            SELECT s.document_id, s.filename, d.id AS available_id
            FROM conversation_documents s LEFT JOIN documents d ON d.id = s.document_id AND d.owner_id = ?
            WHERE s.conversation_id = ? ORDER BY s.selection_index
            """, (rs, row) -> new SelectedDocument(rs.getObject("document_id", UUID.class),
                rs.getString("filename"), rs.getObject("available_id") != null), owner, conversation);
        return new DocumentContext(enabled, selected);
    }

    public DocumentContext resolve(UUID conversation, UUID owner, boolean enabled, List<UUID> ids) {
        var previous = get(conversation, owner);
        if (ids == null) return new DocumentContext(enabled, previous.documents());
        var result = new ArrayList<SelectedDocument>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            var names = jdbc.query("SELECT filename FROM documents WHERE id = ? AND owner_id = ?",
                (rs, row) -> rs.getString(1), id, owner);
            if (!names.isEmpty()) result.add(new SelectedDocument(id, names.getFirst(), true));
            else result.add(previous.documents().stream().filter(d -> d.id().equals(id)).findFirst()
                .map(d -> new SelectedDocument(d.id(), d.filename(), false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected document not found")));
        }
        return new DocumentContext(enabled, List.copyOf(result));
    }

    public void save(UUID conversation, DocumentContext context) {
        jdbc.update("UPDATE conversations SET use_documents = ? WHERE id = ?", context.useDocuments(), conversation);
        jdbc.update("DELETE FROM conversation_documents WHERE conversation_id = ?", conversation);
        for (int i = 0; i < context.documents().size(); i++) {
            var d = context.documents().get(i);
            jdbc.update("INSERT INTO conversation_documents VALUES (?, ?, ?, ?)", conversation, d.id(), d.filename(), i);
        }
    }
}
