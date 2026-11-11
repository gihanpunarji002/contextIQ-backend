package com.gihan.contextioq.ContextIQ.document;

import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FolderRepository {
    private final JdbcTemplate jdbc;
    public FolderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<FolderView> list(UUID owner) {
        return jdbc.query("""
            SELECT f.*, (SELECT COUNT(*) FROM documents d WHERE d.folder_id = f.id AND d.owner_id = f.owner_id) AS document_count
            FROM document_folders f WHERE f.owner_id = ? ORDER BY LOWER(f.name), f.id
            """, (rs, row) -> new FolderView(rs.getObject("id", UUID.class), rs.getString("name"),
                Icon.valueOf(rs.getString("icon")), Color.valueOf(rs.getString("color")),
                rs.getLong("document_count"), rs.getTimestamp("created_at").toInstant()), owner);
    }
    public boolean exists(UUID id, UUID owner) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*) > 0 FROM document_folders WHERE id = ? AND owner_id = ?", Boolean.class, id, owner));
    }
    public FolderView create(UUID owner, FolderRequest request) {
        var id = UUID.randomUUID();
        var now = Instant.now();
        jdbc.update("INSERT INTO document_folders VALUES (?, ?, ?, ?, ?, ?)", id, owner, request.name(), request.icon().name(), request.color().name(), Timestamp.from(now));
        return new FolderView(id, request.name(), request.icon(), request.color(), 0, now);
    }
    public boolean update(UUID id, UUID owner, FolderRequest request) {
        return jdbc.update("UPDATE document_folders SET name = ?, icon = ?, color = ? WHERE id = ? AND owner_id = ?",
            request.name(), request.icon().name(), request.color().name(), id, owner) == 1;
    }
    public boolean move(UUID document, UUID owner, UUID folder) {
        return jdbc.update("UPDATE documents SET folder_id = ? WHERE id = ? AND owner_id = ?", folder, document, owner) == 1;
    }
}
