package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import com.gihan.contextioq.ContextIQ.ai.service.AiChatService;
import com.gihan.contextioq.ContextIQ.document.*;
import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess;
import com.gihan.contextioq.ContextIQ.chat.repository.ConversationRepository;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class WorkspaceIsolationTests {
    @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired WorkspaceAccess access;
    @Autowired DocumentRepository documents; @Autowired FolderRepository folders; @Autowired ConversationRepository conversations;
    @Autowired JdbcTemplate jdbc; @Autowired DataSource dataSource;
    @MockitoBean AiChatService ai;
    UUID admin,editor,viewer,organization,hr,finance,otherOrg,otherWorkspace;
    @BeforeEach void setup() throws Exception {
        admin=account(); editor=account(); viewer=account();
        organization=organization("Team"); otherOrg=organization("Other");
        hr=workspace(organization,"HR"); finance=workspace(organization,"Finance");otherWorkspace=workspace(otherOrg,"Other team");
        for(UUID user:List.of(editor,viewer)) { jdbc.update("INSERT INTO organization_members VALUES (?, ?, 'MEMBER', TRUE)",organization,user); }
        member(hr,editor,"EDITOR");member(hr,viewer,"VIEWER");
        when(ai.status()).thenReturn(new AiChatService.Status("OLLAMA","test",true));
        when(ai.reply(anyList())).thenReturn(new AiChatService.Reply("Test answer","OLLAMA","test",1,1));
    }
    UUID account() { UUID id=users.save(new AppUser("Tenant","Test",UUID.randomUUID()+"@example.com","test",null)).getId();access.ensurePersonal(id);return id; }
    UUID organization(String prefix) throws Exception {
        String name=prefix+UUID.randomUUID();
        mvc.perform(auth(post("/api/v1/organizations"),admin,null).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\""+name+"\"}")).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM organizations WHERE name=?",UUID.class,name);
    }
    UUID workspace(UUID org,String name) throws Exception {
        mvc.perform(auth(post("/api/v1/organizations/"+org+"/workspaces"),admin,null).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\""+name+"\",\"description\":\"Test\"}")).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM workspaces WHERE organization_id=? AND name=?",UUID.class,org,name);
    }
    void member(UUID ws,UUID user,String role) { jdbc.update("INSERT INTO workspace_members VALUES (?, ?, ?, ?, TRUE, ?)",ws,organization,user,role,UUID.randomUUID()); }
    MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request,UUID user,UUID workspace) { request.with(user(user.toString())).with(csrf());if(workspace!=null)request.header("X-Workspace-Id",workspace.toString());return request; }
    DocumentView document(UUID workspace,String name) { return documents.insert(access.require(workspace,admin),name,10,"nomic",List.of("Evidence"),List.of(new float[]{1})); }

    @Test void membersShareOnlyTheirWorkspaceAndViewerCannotMutate() throws Exception {
        var hrDoc=document(hr,"policy.txt");var financeDoc=document(finance,"confidential.txt");
        mvc.perform(auth(get("/api/v1/documents"),viewer,hr)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].createdBy").value(admin.toString()));
        mvc.perform(auth(get("/api/v1/documents"),viewer,finance)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/documents/"+hrDoc.id()),viewer,hr)).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/v1/folders"),viewer,hr).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No\",\"icon\":\"WORK\",\"color\":\"BLUE\"}")).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/documents/"+financeDoc.id()),editor,hr)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/documents/"+hrDoc.id()),editor,hr)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/workspaces"),viewer,null)).andExpect(jsonPath("$.length()").value(2)); // personal + HR, no Finance
        mvc.perform(auth(get("/api/v1/documents"),admin,null).header("X-Workspace-Id","not-a-uuid")).andExpect(status().isBadRequest());
    }
    @Test void selectingCrossWorkspaceSourcesAndReadingAnotherUsersChatAreRejected() throws Exception {
        var secret=document(finance,"finance.txt");
        mvc.perform(auth(post("/api/v1/chat/messages"),viewer,hr).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Read this\",\"documentIds\":[\""+secret.id()+"\"]}")).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/v1/chat/messages"),viewer,hr).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Private HR chat\"}")).andExpect(status().isOk());
        UUID conversation=conversations.findByOwnerId(viewer,org.springframework.data.domain.Sort.unsorted()).getFirst().getId();
        mvc.perform(auth(get("/api/v1/chat/conversations/"+conversation+"/messages"),admin,hr)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/chat/conversations/"+conversation+"/messages"),viewer,viewer)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/chat/conversations/"+conversation+"/messages"),viewer,hr)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/workspaces/"+hr+"/members/"+viewer),admin,null)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/chat/conversations/"+conversation+"/messages"),viewer,hr)).andExpect(status().isNotFound());
    }
    @Test void membershipRevokedDuringGenerationPreventsSavingOrReturningAnswer() throws Exception {
        long before=conversations.count();
        when(ai.reply(anyList())).thenAnswer(call->{
            jdbc.update("UPDATE workspace_members SET active=FALSE,access_version=? WHERE workspace_id=? AND user_id=?",UUID.randomUUID(),hr,viewer);
            return new AiChatService.Reply("Must not be exposed","OLLAMA","test",1,1);
        });
        mvc.perform(auth(post("/api/v1/chat/messages"),viewer,hr).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"A long-running question\"}"))
            .andExpect(status().isNotFound());
        assertEquals(before,conversations.count());
    }
    @Test void adminRulesAndNestedFolderConsistency() throws Exception {
        mvc.perform(auth(delete("/api/v1/workspaces/"+hr),editor,null)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/v1/workspaces/"+hr+"/members/"+admin),admin,null)).andExpect(status().isConflict());
        var root=folders.create(access.require(hr,admin),new FolderRequest("Policies",Icon.FOLDER,Color.BLUE));
        var child=folders.create(access.require(hr,admin),new FolderRequest("Leave",Icon.FOLDER,Color.BLUE,root.id()));
        var doc=document(hr,"leave.txt");folders.move(doc.id(),hr,child.id());
        assertEquals(Set.of(root.id(),child.id()),new HashSet<>(folders.descendants(hr,List.of(root.id()))));
        assertEquals(1,folders.documentCount(hr,root.id()));
        mvc.perform(auth(post("/api/v1/folders/"+root.id()),editor,hr).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Policies\",\"icon\":\"WORK\",\"color\":\"BLUE\",\"parentId\":\""+child.id()+"\"}")).andExpect(status().isConflict());
        var chat = conversations.saveAndFlush(new com.gihan.contextioq.ContextIQ.chat.entity.Conversation("Tenant constraint",admin,hr,organization));
        var financeDocument = document(finance,"cross-workspace.txt");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
            "INSERT INTO conversation_documents VALUES (?, ?, 'foreign.txt', 0, ?, ?)",chat.getId(),financeDocument.id(),hr,organization));
        var foreign=folders.create(access.require(otherWorkspace,admin),new FolderRequest("Other",Icon.FOLDER,Color.BLUE));
        mvc.perform(auth(post("/api/v1/folders/move"),editor,hr).contentType(MediaType.APPLICATION_JSON).content("{\"documentIds\":[\""+doc.id()+"\"],\"folderId\":\""+foreign.id()+"\"}")).andExpect(status().isNotFound());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE documents SET folder_id=? WHERE id=?",foreign.id(),doc.id()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE document_folders SET parent_id=? WHERE id=?",foreign.id(),root.id()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO workspace_members VALUES (?, ?, ?, 'VIEWER', TRUE, ?)",hr,otherOrg,viewer,UUID.randomUUID()));
    }
    @Test void organizationAndWorkspaceMembershipAreSeparateAndDeletionRevokesAccess() throws Exception {
        UUID newcomer=account();String email=users.findById(newcomer).orElseThrow().getEmail();
        String roleBody="{\"email\":\""+email+"\",\"role\":\"VIEWER\"}";
        mvc.perform(auth(post("/api/v1/workspaces/"+hr+"/members"),admin,null).contentType(MediaType.APPLICATION_JSON).content(roleBody)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/v1/organizations/"+organization+"/members"),editor,null).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}")).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/v1/organizations/"+organization+"/members"),admin,null).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}")).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/documents"),newcomer,hr)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/v1/workspaces/"+hr+"/members"),admin,null).contentType(MediaType.APPLICATION_JSON).content(roleBody)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/documents"),newcomer,hr)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/v1/workspaces/"+hr+"/members"),newcomer,null).contentType(MediaType.APPLICATION_JSON).content(roleBody.replace("VIEWER","ADMIN"))).andExpect(status().isForbidden());
        var retained=document(hr,"retained.txt");
        mvc.perform(auth(delete("/api/v1/workspaces/"+hr),admin,null)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/documents"),admin,hr)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/documents"),newcomer,hr)).andExpect(status().isNotFound());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE id=?",Integer.class,retained.id()));
    }

    @Test void actualRetrievalSqlChecksMembershipBeforeRanking() {
        var allowed=document(hr,"hr.txt");var denied=document(finance,"finance.txt");
        var scoped=new DocumentRepository(new JdbcTemplate(dataSource) {
            @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object...args) {
                return super.query(sql.replace("c.embedding <=> CAST(? AS vector)","0 * LENGTH(CAST(? AS VARCHAR))"),mapper,args);
            }
        },"text");
        var result=scoped.searchScoped(hr,viewer,"nomic",new float[]{1},.45,List.of(allowed.id(),denied.id()),List.of());
        assertEquals(List.of(allowed.id()),result.stream().map(s->s.documentId()).toList());
        jdbc.update("UPDATE organization_members SET role='ADMIN' WHERE organization_id=? AND user_id=?",organization,viewer);
        assertTrue(scoped.searchScoped(finance,viewer,"nomic",new float[]{1},.45,List.of(denied.id()),List.of()).isEmpty());
        jdbc.update("UPDATE organization_members SET active=FALSE WHERE organization_id=? AND user_id=?",organization,viewer);
        assertTrue(scoped.searchScoped(hr,viewer,"nomic",new float[]{1},.45,List.of(allowed.id()),List.of()).isEmpty());
    }
}
