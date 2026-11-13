package com.gihan.contextioq.ContextIQ;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WorkspaceMigrationTests {
    @Test void existingKnowledgeAndDeletedSourceSnapshotsRemainInPrivatePersonalWorkspace() {
        String url="jdbc:h2:mem:migration_"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration","classpath:db/h2").target("7").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID user=UUID.randomUUID(),doc=UUID.randomUUID(),conversation=UUID.randomUUID(),deleted=UUID.randomUUID(),anonymous=UUID.randomUUID();
        jdbc.update("INSERT INTO app_users(id,first_name,last_name,email,password_hash,created_at) VALUES (?, 'Old','User','old@example.com','test',CURRENT_TIMESTAMP)",user);
        jdbc.update("INSERT INTO documents(id,owner_id,filename,size_bytes,chunk_count,embedding_model,created_at) VALUES (?, ?, 'old.txt', 10, 1, 'nomic',CURRENT_TIMESTAMP)",doc,user);
        jdbc.update("INSERT INTO conversations(id,title,created_at,updated_at,owner_id) VALUES (?, 'Old chat',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",conversation,user);
        jdbc.update("INSERT INTO conversations(id,title,created_at,updated_at) VALUES (?, 'Anonymous',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",anonymous);
        jdbc.update("INSERT INTO conversation_documents VALUES (?, ?, 'deleted.txt', 0)",conversation,deleted);
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration","classpath:db/h2").load().migrate();
        assertEquals(user,jdbc.queryForObject("SELECT workspace_id FROM documents WHERE id=?",UUID.class,doc));
        assertEquals(user,jdbc.queryForObject("SELECT owner_id FROM conversations WHERE id=?",UUID.class,conversation));
        assertEquals(user,jdbc.queryForObject("SELECT workspace_id FROM workspace_resources WHERE id=?",UUID.class,deleted));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND user_id=? AND role='ADMIN'",Integer.class,user,user));
        UUID quarantine=jdbc.queryForObject("SELECT workspace_id FROM conversations WHERE id=?",UUID.class,anonymous);
        assertTrue(jdbc.queryForObject("SELECT deleted FROM workspaces WHERE id=?",Boolean.class,quarantine));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=?",Integer.class,quarantine));
    }
}
