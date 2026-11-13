package com.gihan.contextioq.ContextIQ.workspace;

import com.gihan.contextioq.ContextIQ.auth.service.CurrentUser;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceAccess {
    public enum Role { ADMIN, EDITOR, VIEWER }
    public record Scope(UUID workspaceId, UUID organizationId, UUID userId, Role role, UUID version) {
        public void edit() { if (role == Role.VIEWER) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Editor access is required"); }
        public void admin() { if (role != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace admin access is required"); }
    }
    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final TransactionTemplate transactions;
    public WorkspaceAccess(JdbcTemplate jdbc, CurrentUser current, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.current=current; transactions=new TransactionTemplate(manager); transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    // Serializing on the user row makes first-login provisioning safe under concurrent requests.
    public void ensurePersonal(UUID user) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM organizations WHERE id=?", Integer.class, user) > 0) return;
        transactions.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM app_users WHERE id=? FOR UPDATE", UUID.class, user);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM organizations WHERE id=?", Integer.class, user) > 0) return;
            jdbc.update("INSERT INTO organizations VALUES (?, 'Personal organization', ?, CURRENT_TIMESTAMP)", user,user);
            jdbc.update("INSERT INTO organization_members VALUES (?, ?, 'ADMIN', TRUE)",user,user);
            jdbc.update("INSERT INTO workspaces VALUES (?, ?, 'Personal', 'Your private knowledge workspace.', ?, CURRENT_TIMESTAMP, TRUE, FALSE)",user,user,user);
            jdbc.update("INSERT INTO workspace_members VALUES (?, ?, ?, 'ADMIN', TRUE, ?)",user,user,user,UUID.randomUUID());
        });
    }
    public Scope current() {
        UUID user=current.get().getId();
        String header=null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)
            header=request.getRequest().getHeader("X-Workspace-Id");
        UUID workspace=user;
        if (header != null) try { workspace=UUID.fromString(header); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid workspace ID"); }
        if (workspace.equals(user)) ensurePersonal(user);
        return require(workspace,user);
    }
    public Scope require(UUID workspace, UUID user) {
        return jdbc.query("""
            SELECT w.organization_id, wm.role, wm.access_version FROM workspaces w
            JOIN workspace_members wm ON wm.workspace_id=w.id AND wm.organization_id=w.organization_id
            JOIN organization_members om ON om.organization_id=w.organization_id AND om.user_id=wm.user_id
            WHERE w.id=? AND wm.user_id=? AND wm.active=TRUE AND om.active=TRUE AND w.deleted=FALSE
            """,(rs,row)->new Scope(workspace,rs.getObject(1,UUID.class),user,Role.valueOf(rs.getString(2)),rs.getObject(3,UUID.class)),workspace,user)
            .stream().findFirst().orElseThrow(WorkspaceAccess::missing);
    }
    /** Call in a short transaction. Membership writers take the same lock. Never lock across inference. */
    public Scope lock(Scope previous) {
        jdbc.queryForObject("SELECT id FROM workspaces WHERE id=? FOR UPDATE",UUID.class,previous.workspaceId());
        var scope=require(previous.workspaceId(),previous.userId());
        if (!scope.version().equals(previous.version())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Workspace permissions changed. Reload and retry.");
        return scope;
    }
    public static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Workspace or resource not found"); }
}
