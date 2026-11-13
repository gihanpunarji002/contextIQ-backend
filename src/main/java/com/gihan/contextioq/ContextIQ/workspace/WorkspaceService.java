package com.gihan.contextioq.ContextIQ.workspace;

import com.gihan.contextioq.ContextIQ.auth.service.CurrentUser;
import jakarta.validation.constraints.*;
import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {
    public record NameRequest(@NotBlank @Size(max=80) String name) {}
    public record WorkspaceRequest(@NotBlank @Size(max=80) String name, @Size(max=500) String description) {}
    public record OrganizationMemberRequest(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email) {}
    public record MemberRequest(@NotBlank @jakarta.validation.constraints.Email String email, @NotNull WorkspaceAccess.Role role) {}
    public record OrganizationView(UUID id,String name,String role) {}
    public record WorkspaceView(UUID id,UUID organizationId,String organizationName,String name,String description,
        String role,boolean personal,long documentCount,long folderCount,long memberCount,UUID createdBy,Instant createdAt) {}
    public record MemberView(UUID userId,String firstName,String lastName,String email,String role) {}
    private final JdbcTemplate jdbc; private final CurrentUser current; private final WorkspaceAccess access;
    public WorkspaceService(JdbcTemplate jdbc,CurrentUser current,WorkspaceAccess access) { this.jdbc=jdbc;this.current=current;this.access=access; }
    public List<OrganizationView> organizations() {
        UUID user=current.get().getId(); access.ensurePersonal(user);
        return jdbc.query("SELECT o.id,o.name,m.role FROM organizations o JOIN organization_members m ON m.organization_id=o.id WHERE m.user_id=? AND m.active=TRUE ORDER BY o.name,o.id",
            (rs,row)->new OrganizationView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),user);
    }
    @Transactional public OrganizationView createOrganization(NameRequest request) {
        UUID user=current.get().getId(),id=UUID.randomUUID(); String name=name(request.name());
        jdbc.update("INSERT INTO organizations VALUES (?, ?, ?, CURRENT_TIMESTAMP)",id,name,user);
        jdbc.update("INSERT INTO organization_members VALUES (?, ?, 'ADMIN', TRUE)",id,user);
        return new OrganizationView(id,name,"ADMIN");
    }
    private void orgAdmin(UUID organization) {
        var roles=jdbc.queryForList("SELECT role FROM organization_members WHERE organization_id=? AND user_id=? AND active=TRUE",String.class,organization,current.get().getId());
        if (roles.isEmpty()) throw WorkspaceAccess.missing();
        if (!roles.getFirst().equals("ADMIN")) throw forbidden("Organization admin access is required");
    }
    public List<WorkspaceView> list() {
        UUID user=current.get().getId(); access.ensurePersonal(user);
        return jdbc.query("""
            SELECT w.*,o.name AS organization_name,wm.role,
             (SELECT COUNT(*) FROM documents d WHERE d.workspace_id=w.id) AS documents,
             (SELECT COUNT(*) FROM document_folders f WHERE f.workspace_id=w.id) AS folders,
             (SELECT COUNT(*) FROM workspace_members m JOIN organization_members om ON om.organization_id=m.organization_id AND om.user_id=m.user_id WHERE m.workspace_id=w.id AND m.active=TRUE AND om.active=TRUE) AS members
            FROM workspaces w JOIN organizations o ON o.id=w.organization_id
            JOIN workspace_members wm ON wm.workspace_id=w.id AND wm.organization_id=w.organization_id
            JOIN organization_members om ON om.organization_id=w.organization_id AND om.user_id=wm.user_id
            WHERE wm.user_id=? AND wm.active=TRUE AND om.active=TRUE AND w.deleted=FALSE ORDER BY w.personal DESC,w.name,w.id
            """,(rs,row)->new WorkspaceView(rs.getObject("id",UUID.class),rs.getObject("organization_id",UUID.class),rs.getString("organization_name"),rs.getString("name"),rs.getString("description"),rs.getString("role"),rs.getBoolean("personal"),rs.getLong("documents"),rs.getLong("folders"),rs.getLong("members"),rs.getObject("created_by",UUID.class),rs.getTimestamp("created_at").toInstant()),user);
    }
    public WorkspaceView get(UUID id) {
        access.require(id,current.get().getId());
        return list().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow(WorkspaceAccess::missing);
    }
    @Transactional public WorkspaceView create(UUID organization,WorkspaceRequest request) {
        orgAdmin(organization); UUID id=UUID.randomUUID(),user=current.get().getId();
        try { jdbc.update("INSERT INTO workspaces VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, FALSE, FALSE)",id,organization,name(request.name()),description(request.description()),user); }
        catch (DuplicateKeyException e) { throw conflict("A workspace with this name already exists"); }
        jdbc.update("INSERT INTO workspace_members VALUES (?, ?, ?, 'ADMIN', TRUE, ?)",id,organization,user,UUID.randomUUID());
        return get(id);
    }
    @Transactional public WorkspaceView update(UUID id,WorkspaceRequest request) {
        var scope=access.lock(access.require(id,current.get().getId()));scope.admin();
        try { jdbc.update("UPDATE workspaces SET name=?,description=? WHERE id=?",name(request.name()),description(request.description()),id); }
        catch (DuplicateKeyException e) { throw conflict("A workspace with this name already exists"); }
        return get(id);
    }
    @Transactional public void delete(UUID id) {
        var scope=access.lock(access.require(id,current.get().getId())); scope.admin();
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT personal FROM workspaces WHERE id=?",Boolean.class,id))) throw conflict("Personal workspaces cannot be deleted");
        jdbc.update("UPDATE workspaces SET deleted=TRUE WHERE id=?",id);
        jdbc.update("UPDATE workspace_members SET access_version=? WHERE workspace_id=?",UUID.randomUUID(),id);
    }
    public List<MemberView> members(UUID workspace) {
        var scope=access.require(workspace,current.get().getId());scope.admin();
        return jdbc.query("SELECT u.id,u.first_name,u.last_name,u.email,m.role FROM workspace_members m JOIN app_users u ON u.id=m.user_id WHERE m.workspace_id=? AND m.active=TRUE ORDER BY u.email",
            (rs,row)->new MemberView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),workspace);
    }
    public List<MemberView> organizationMembers(UUID organization) {
        orgAdmin(organization);
        return jdbc.query("SELECT u.id,u.first_name,u.last_name,u.email,m.role FROM organization_members m JOIN app_users u ON u.id=m.user_id WHERE m.organization_id=? AND m.active=TRUE ORDER BY u.email",
            (rs,row)->new MemberView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)),organization);
    }
    @Transactional public void addOrganizationMember(UUID organization,OrganizationMemberRequest request) {
        orgAdmin(organization);
        jdbc.queryForObject("SELECT id FROM organizations WHERE id=? FOR UPDATE",UUID.class,organization);
        UUID user=findUser(request.email());
        // Adding organization membership does not grant workspace access or organization administration.
        if (jdbc.update("UPDATE organization_members SET active=TRUE WHERE organization_id=? AND user_id=?",organization,user)==0)
            jdbc.update("INSERT INTO organization_members VALUES (?, ?, 'MEMBER', TRUE)",organization,user);
    }
    @Transactional public void setMember(UUID workspace,MemberRequest request) {
        var scope=access.lock(access.require(workspace,current.get().getId()));scope.admin();
        if (get(workspace).personal()) throw conflict("Personal workspaces cannot be shared");
        UUID user=findUser(request.email());
        if (jdbc.queryForObject("SELECT COUNT(*) FROM organization_members WHERE organization_id=? AND user_id=? AND active=TRUE",Integer.class,scope.organizationId(),user)==0)
            throw conflict("Add this account to the organization before assigning a workspace role");
        protectLastAdmin(workspace,user,request.role()!=WorkspaceAccess.Role.ADMIN);
        if (jdbc.update("UPDATE workspace_members SET role=?,active=TRUE,access_version=? WHERE workspace_id=? AND user_id=?",request.role().name(),UUID.randomUUID(),workspace,user)==0)
            jdbc.update("INSERT INTO workspace_members VALUES (?, ?, ?, ?, TRUE, ?)",workspace,scope.organizationId(),user,request.role().name(),UUID.randomUUID());
    }
    @Transactional public void removeMember(UUID workspace,UUID user) {
        var scope=access.lock(access.require(workspace,current.get().getId()));scope.admin();
        protectLastAdmin(workspace,user,true);
        if (jdbc.update("UPDATE workspace_members SET active=FALSE,access_version=? WHERE workspace_id=? AND user_id=? AND active=TRUE",UUID.randomUUID(),workspace,user)==0) throw WorkspaceAccess.missing();
    }
    private void protectLastAdmin(UUID workspace,UUID user,boolean removingAdmin) {
        if (!removingAdmin) return;
        int isAdmin=jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND user_id=? AND active=TRUE AND role='ADMIN'",Integer.class,workspace,user);
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM workspace_members WHERE workspace_id=? AND active=TRUE AND role='ADMIN'",Integer.class,workspace);
        if (isAdmin>0 && count<=1) throw conflict("A workspace must retain at least one admin");
    }
    private UUID findUser(String email) { return jdbc.queryForList("SELECT id FROM app_users WHERE LOWER(email)=?",UUID.class,email.strip().toLowerCase(Locale.ROOT)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Registered account not found")); }
    private String name(String name) { String clean=name.strip(); if (clean.isBlank() || clean.chars().anyMatch(Character::isISOControl)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a name without control characters");return clean; }
    private String description(String value) { return value==null?"":value.strip(); }
    private ResponseStatusException forbidden(String detail) { return new ResponseStatusException(HttpStatus.FORBIDDEN,detail); }
    private ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT,detail); }
}
