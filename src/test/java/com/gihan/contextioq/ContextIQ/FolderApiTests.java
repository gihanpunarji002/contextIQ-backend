package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;

import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import com.gihan.contextioq.ContextIQ.document.*;
import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import com.gihan.contextioq.ContextIQ.chat.entity.Conversation;
import com.gihan.contextioq.ContextIQ.chat.repository.ConversationRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FolderApiTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired FolderRepository folders;
    @Autowired DocumentRepository documents;
    @Autowired ConversationRepository conversations;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean org.springframework.ai.embedding.EmbeddingModel embedding;
    UUID owner, stranger;

    @BeforeEach void setup() { owner = account(); stranger = account(); }
    UUID account() { return users.save(new AppUser("Folder", "Test", UUID.randomUUID() + "@example.com", "test", null)).getId(); }
    FolderView folder(UUID account) { return folders.create(account, new FolderRequest("Work", Icon.WORK, Color.BLUE)); }
    DocumentView document(UUID account, String name) { return documents.insert(account, name, 10, "nomic", List.of("Evidence"), List.of(new float[]{1})); }

    @Test void createCustomizeAndValidateFolders() throws Exception {
        String body = "{\"name\":\"  Accounts  \",\"icon\":\"ACCOUNTS\",\"color\":\"GREEN\"}";
        mvc.perform(post("/api/v1/folders").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Accounts"));
        UUID id = folders.list(owner).getFirst().id();
        mvc.perform(post("/api/v1/folders/" + id).with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Agreements\",\"icon\":\"AGREEMENT\",\"color\":\"VIOLET\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.icon").value("AGREEMENT"));
        mvc.perform(get("/api/v1/folders").with(user(owner.toString())))
            .andExpect(jsonPath("$[0].name").value("Agreements")).andExpect(jsonPath("$[0].color").value("VIOLET"));
        for (String invalid : List.of("{}", "{\"name\":\" \",\"icon\":\"WORK\",\"color\":\"BLUE\"}",
            "{\"name\":\"Test\",\"icon\":\"EXTERNAL_SVG\",\"color\":\"BLUE\"}")) {
            mvc.perform(post("/api/v1/folders").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/folders").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Agreements\",\"icon\":\"WORK\",\"color\":\"BLUE\"}"))
            .andExpect(status().isConflict());
    }

    @Test void folderAccessRequiresOwnerAndCsrf() throws Exception {
        var f = folder(owner);
        mvc.perform(get("/api/v1/folders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/folders").with(user(stranger.toString()))).andExpect(jsonPath("$.length()").value(0));
        String body = "{\"name\":\"Changed\",\"icon\":\"WORK\",\"color\":\"BLUE\"}";
        mvc.perform(post("/api/v1/folders/" + f.id()).with(user(stranger.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/folders").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
    }

    @Test void bulkMoveIsAtomicAndPreservesChunks() throws Exception {
        var f = folder(owner);
        var a = document(owner, "a.txt"); var b = document(owner, "b.txt"); var foreign = document(stranger, "private.txt");
        String move = "{\"folderId\":\"" + f.id() + "\",\"documentIds\":[\"" + a.id() + "\",\"" + b.id() + "\"]}";
        mvc.perform(post("/api/v1/folders/move").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(move))
            .andExpect(status().isNoContent());
        assertEquals(2, folders.list(owner).getFirst().documentCount());
        assertTrue(documents.list(owner).stream().allMatch(d -> f.id().equals(d.folderId())));
        var chunkIds = jdbc.queryForList("SELECT id FROM document_chunks WHERE document_id = ?", UUID.class, a.id());
        mvc.perform(post("/api/v1/folders/move").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"documentIds\":[\"" + a.id() + "\",\"" + foreign.id() + "\"]}"))
            .andExpect(status().isNotFound());
        assertEquals(f.id(), documents.list(owner).stream().filter(d -> d.id().equals(a.id())).findFirst().orElseThrow().folderId());
        mvc.perform(post("/api/v1/folders/move").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"documentIds\":[\"" + a.id() + "\"]}"))
            .andExpect(status().isNoContent());
        assertEquals(chunkIds, jdbc.queryForList("SELECT id FROM document_chunks WHERE document_id = ?", UUID.class, a.id()));
        assertEquals(1, folders.list(owner).getFirst().documentCount());
        mvc.perform(post("/api/v1/folders/move").with(user(stranger.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"folderId\":\"" + f.id() + "\",\"documentIds\":[\"" + foreign.id() + "\"]}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/folders/move").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"documentIds\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test void chatRemembersFoldersAndReflectsCurrentMembershipWithoutLosingFiles() throws Exception {
        var f = folder(owner); var foreign = folder(stranger); var doc = document(owner, "explicit.txt");
        var conversation = conversations.saveAndFlush(new Conversation("Folder scope", owner));
        String route = "/api/v1/chat/conversations/" + conversation.getId() + "/documents";
        mvc.perform(post(route).with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"useDocuments\":true,\"documentIds\":[\"" + doc.id() + "\"],\"folderIds\":[\"" + f.id() + "\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.folders[0].documentCount").value(0));
        var added = document(owner, "added.txt"); folders.move(added.id(), owner, f.id());
        mvc.perform(get(route).with(user(owner.toString())))
            .andExpect(jsonPath("$.folders[0].id").value(f.id().toString()))
            .andExpect(jsonPath("$.folders[0].documentCount").value(1))
            .andExpect(jsonPath("$.documents[0].id").value(doc.id().toString()));
        mvc.perform(post(route).with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"useDocuments\":true,\"documentIds\":[],\"folderIds\":[\"" + foreign.id() + "\"]}"))
            .andExpect(status().isNotFound());
        mvc.perform(get(route).with(user(owner.toString()))).andExpect(jsonPath("$.documents.length()").value(1));
        folders.move(added.id(), owner, null);
        mvc.perform(get(route).with(user(owner.toString()))).andExpect(jsonPath("$.folders[0].documentCount").value(0));
        mvc.perform(post(route).with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"useDocuments\":false,\"documentIds\":[],\"folderIds\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.folders.length()").value(0));
    }

    @Test void uploadIntoFolderChecksOwnershipBeforeEmbedding() throws Exception {
        var f = folder(owner);
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", "Project notes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/documents").file(file).param("folderId", f.id().toString()).with(user(stranger.toString())).with(csrf()))
            .andExpect(status().isNotFound());
        verifyNoInteractions(embedding);
        float[] vector = new float[768]; vector[0] = 1;
        when(embedding.embed(anyList())).thenReturn(List.of(vector));
        mvc.perform(multipart("/api/v1/documents").file(file).param("folderId", f.id().toString()).with(user(owner.toString())).with(csrf()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.folderId").value(f.id().toString()));
        assertEquals(1, folders.list(owner).getFirst().documentCount());
    }
}
