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
    private final com.gihan.contextioq.ContextIQ.document.FolderRepository folderRepository;
    public ConversationDocuments(JdbcTemplate jdbc, com.gihan.contextioq.ContextIQ.document.FolderRepository folderRepository) { this.jdbc = jdbc; this.folderRepository = folderRepository; }

    public DocumentContext get(UUID conversation, UUID owner) {
        if (conversation == null) return new DocumentContext(false, List.of());
        boolean enabled = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT use_documents FROM conversations WHERE id = ? AND workspace_id = ?", Boolean.class, conversation, owner));
        var selected = jdbc.query("""
            SELECT s.document_id, s.filename, d.id AS available_id
            FROM conversation_documents s LEFT JOIN documents d ON d.id = s.document_id AND d.workspace_id = ?
            WHERE s.conversation_id = ? ORDER BY s.selection_index
            """, (rs, row) -> new SelectedDocument(rs.getObject("document_id", UUID.class),
                rs.getString("filename"), rs.getObject("available_id") != null), owner, conversation);
        var folders = jdbc.query("""
            SELECT s.folder_id, COALESCE(f.name, s.name) AS name, f.id AS available_id,
              (SELECT COUNT(*) FROM documents d WHERE d.folder_id = f.id AND d.workspace_id = ?) AS document_count
            FROM conversation_folders s LEFT JOIN document_folders f ON f.id = s.folder_id AND f.workspace_id = ?
            WHERE s.conversation_id = ? ORDER BY s.selection_index
            """, (rs, row) -> new SelectedFolder(rs.getObject("folder_id", UUID.class), rs.getString("name"),
                rs.getObject("available_id") != null, rs.getLong("document_count")), owner, owner, conversation);
        return new DocumentContext(enabled, selected, folders.stream().map(f -> new SelectedFolder(f.id(), f.name(), f.available(), f.available() ? folderRepository.documentCount(owner, f.id()) : 0)).toList());
    }

    public DocumentContext resolve(UUID conversation, UUID owner, boolean enabled, List<UUID> ids, List<UUID> folderIds) {
        var previous = get(conversation, owner);

        var result = new ArrayList<SelectedDocument>();
        for (UUID id : new LinkedHashSet<>(ids == null ? previous.documents().stream().map(SelectedDocument::id).toList() : ids)) {
            var names = jdbc.query("SELECT filename FROM documents WHERE id = ? AND workspace_id = ?",
                (rs, row) -> rs.getString(1), id, owner);
            if (!names.isEmpty()) result.add(new SelectedDocument(id, names.getFirst(), true));
            else result.add(previous.documents().stream().filter(d -> d.id().equals(id)).findFirst()
                .map(d -> new SelectedDocument(d.id(), d.filename(), false))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected document not found")));
        }
        var selectedFolders = new ArrayList<SelectedFolder>();
        for (UUID id : new LinkedHashSet<>(folderIds == null ? previous.folders().stream().map(SelectedFolder::id).toList() : folderIds)) {
            var found = jdbc.query("""
                SELECT f.name, (SELECT COUNT(*) FROM documents d WHERE d.folder_id = f.id AND d.workspace_id = ?) AS document_count
                FROM document_folders f WHERE f.id = ? AND f.workspace_id = ?
                """, (rs, row) -> new SelectedFolder(id, rs.getString("name"), true, rs.getLong("document_count")), owner, id, owner);
            if (!found.isEmpty()) { var f = found.getFirst(); selectedFolders.add(new SelectedFolder(f.id(), f.name(), true, folderRepository.documentCount(owner, f.id()))); }
            else selectedFolders.add(previous.folders().stream().filter(f -> f.id().equals(id)).findFirst()
                .map(f -> new SelectedFolder(f.id(), f.name(), false, 0))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected folder not found")));
        }
        return new DocumentContext(enabled, List.copyOf(result), List.copyOf(selectedFolders));
    }

    public void save(UUID conversation, DocumentContext context) {
        jdbc.update("UPDATE conversations SET use_documents = ? WHERE id = ?", context.useDocuments(), conversation);
        jdbc.update("DELETE FROM conversation_folders WHERE conversation_id = ?", conversation);
        for (int i = 0; i < context.folders().size(); i++) {
            var f = context.folders().get(i);
            jdbc.update("INSERT INTO conversation_folders SELECT ?, ?, ?, ?, workspace_id, organization_id FROM conversations WHERE id=?", conversation, f.id(), f.name(), i, conversation);
        }
        jdbc.update("DELETE FROM conversation_documents WHERE conversation_id = ?", conversation);
        for (int i = 0; i < context.documents().size(); i++) {
            var d = context.documents().get(i);
            jdbc.update("INSERT INTO conversation_documents SELECT ?, ?, ?, ?, workspace_id, organization_id FROM conversations WHERE id=?", conversation, d.id(), d.filename(), i, conversation);
        }
    }
}
