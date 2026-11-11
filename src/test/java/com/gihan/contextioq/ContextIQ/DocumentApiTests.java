package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import com.gihan.contextioq.ContextIQ.document.DocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DocumentRepository documents;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmbeddingModel embedding;
    UUID owner;
    UUID stranger;

    @BeforeEach void setup() {
        owner = account();
        stranger = account();
        when(embedding.embed(anyList())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            List<String> texts = call.getArgument(0);
            assertTrue(texts.stream().allMatch(s -> s.startsWith("search_document: ")));
            return texts.stream().map(s -> { float[] v = new float[768]; v[0] = 1; return v; }).toList();
        });
    }

    UUID account() {
        return users.save(new AppUser("Rag", "Test", UUID.randomUUID() + "@example.com", "test-only", null)).getId();
    }

    MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "text/plain", bytes);
    }

    @Test void uploadListOwnershipAndCascadeDelete() throws Exception {
        mvc.perform(multipart("/api/v1/documents")
                .file(file("guide.md", "Project Juniper launches on May 14.".getBytes(StandardCharsets.UTF_8)))
                .with(user(owner.toString())).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.chunkCount").value(1));
        var doc = documents.list(owner).getFirst();
        mvc.perform(get("/api/v1/documents").with(user(owner.toString())))
                .andExpect(jsonPath("$[0].filename").value("guide.md"));
        mvc.perform(get("/api/v1/documents").with(user(stranger.toString())))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/v1/documents/" + doc.id()).with(user(stranger.toString())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/documents/" + doc.id()).with(user(owner.toString())).with(csrf()))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks WHERE document_id = ?", Integer.class, doc.id()));
    }

    @Test void validatesTextAndRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/documents").file(file("a.txt", new byte[]{65})).with(user(owner.toString())))
                .andExpect(status().isForbidden());
        for (byte[] bytes : List.of(new byte[0], new byte[]{(byte) 0xC3}, new byte[]{0})) {
            mvc.perform(multipart("/api/v1/documents").file(file("a.txt", bytes)).with(user(owner.toString())).with(csrf()))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/api/v1/documents").file(file("a.txt", new byte[65537])).with(user(owner.toString())).with(csrf()))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/v1/documents").file(file("a.pdf", new byte[]{65})).with(user(owner.toString())).with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(embedding);
    }

    @Test void deletePreflightAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/documents/" + UUID.randomUUID())
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/documents/" + UUID.randomUUID())
                .header("Origin", "https://untrusted.example").header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test void richUploadsPersistExtractedTextForRetrieval() throws Exception {
        for (var item : Map.of("launch.pdf", DocumentFixtures.pdf("Juniper launches May 14.", false),
                "launch.docx", DocumentFixtures.docx("Juniper launches May 14."),
                "legacy.doc", DocumentFixtures.legacyDoc()).entrySet()) {
            mvc.perform(multipart("/api/v1/documents").file(file(item.getKey(), item.getValue()))
                    .with(user(owner.toString())).with(csrf()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text:latest"));
        }
        assertEquals(3, documents.list(owner).size());
        var text = jdbc.queryForList("SELECT c.content FROM document_chunks c JOIN documents d ON d.id = c.document_id WHERE d.owner_id = ?", String.class, owner);
        assertTrue(text.stream().anyMatch(s -> s.contains("May 14")));
        assertTrue(text.stream().anyMatch(s -> s.contains("Morgan")));
    }

    @Test void extractionFailureDoesNotCallEmbeddingOrPersist() throws Exception {
        mvc.perform(multipart("/api/v1/documents").file(file("locked.pdf", DocumentFixtures.pdf("Secret", true)))
                .with(user(owner.toString())).with(csrf())).andExpect(status().isUnprocessableEntity());
        assertTrue(documents.list(owner).isEmpty());
        verifyNoInteractions(embedding);
    }

    @Test void failedEmbeddingLeavesNoPartialDocument() throws Exception {
        when(embedding.embed(anyList())).thenThrow(new IllegalStateException("offline"));
        mvc.perform(multipart("/api/v1/documents").file(file("a.txt", new byte[]{65})).with(user(owner.toString())).with(csrf()))
                .andExpect(status().isBadGateway());
        assertTrue(documents.list(owner).isEmpty());
    }
}
