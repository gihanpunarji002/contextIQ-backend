package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import com.gihan.contextioq.ContextIQ.document.*;
import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@SpringBootTest
class FolderRetrievalTests {
    @Autowired DataSource dataSource;
    @Autowired UserRepository users;
    @Autowired DocumentRepository documents;
    @Autowired FolderRepository folders;

    @Test void sqlScopeCombinesFoldersAndFilesWithoutDuplicatesOrCrossAccountLeaks() {
        UUID owner = account(), other = account();
        var folder = folders.create(owner, new FolderRequest("Work", Icon.WORK, Color.BLUE));
        var foreignFolder = folders.create(other, new FolderRequest("Private", Icon.FOLDER, Color.SLATE));
        var inFolder = document(owner, "in-folder", "nomic");
        folders.move(inFolder.id(), owner, folder.id());
        var explicit = document(owner, "explicit", "nomic");
        var foreign = document(other, "foreign", "nomic");
        folders.move(foreign.id(), other, foreignFolder.id());
        var wrongModel = document(owner, "different-model", "other-model");
        folders.move(wrongModel.id(), owner, folder.id());
        for (int i = 0; i < 6; i++) document(owner, "unselected-" + i, "nomic");
        // H2 has no pgvector operator. Substitute only distance arithmetic; execute the actual
        // ownership/scope predicates, joins, ordering and LIMIT against the real test tables.
        var scoped = new DocumentRepository(new JdbcTemplate(dataSource) {
            @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                return super.query(sql.replace("c.embedding <=> CAST(? AS vector)", "0 * LENGTH(CAST(? AS VARCHAR))"), mapper, args);
            }
        }, "text");
        var sources = scoped.search(owner, "nomic", new float[]{1}, .45,
            List.of(explicit.id(), inFolder.id(), foreign.id()), List.of(folder.id(), foreignFolder.id()));
        assertEquals(2, sources.size());
        assertEquals(Set.of(explicit.id(), inFolder.id()), new HashSet<>(sources.stream().map(s -> s.documentId()).toList()));
        folders.move(inFolder.id(), owner, null);
        assertTrue(scoped.search(owner, "nomic", new float[]{1}, .45, List.of(), List.of(folder.id())).isEmpty());
        assertTrue(scoped.search(owner, "nomic", new float[]{1}, .45, List.of(), List.of()).isEmpty());
    }
    UUID account() { return users.save(new AppUser("Scope", "Test", UUID.randomUUID() + "@example.com", "test", null)).getId(); }
    DocumentView document(UUID owner, String name, String model) { return documents.insert(owner, name + ".txt", 10, model, List.of("Evidence"), List.of(new float[]{1})); }
}
