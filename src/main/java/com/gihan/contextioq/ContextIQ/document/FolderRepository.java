package com.gihan.contextioq.ContextIQ.document;
import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess;
import com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess.Scope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FolderRepository {
    private final JdbcTemplate jdbc; private final WorkspaceAccess access;
    public FolderRepository(JdbcTemplate jdbc, WorkspaceAccess access) { this.jdbc=jdbc;this.access=access; }
    public List<UUID> descendants(UUID workspace,List<UUID> roots) {
        var parents=new HashMap<UUID,UUID>();
        jdbc.query("SELECT id,parent_id FROM document_folders WHERE workspace_id=?", rs->{
            parents.put(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class));
        },workspace);
        var found=new LinkedHashSet<UUID>();var queue=new ArrayDeque<UUID>();
        for (UUID root:roots) if(parents.containsKey(root)) queue.add(root);
        while(!queue.isEmpty()) { UUID id=queue.remove();if(!found.add(id))continue;
            parents.forEach((child,parent)->{if(id.equals(parent))queue.add(child);}); }
        return List.copyOf(found);
    }
    public long documentCount(UUID workspace,UUID folder) {
        var ids=descendants(workspace,List.of(folder)); if(ids.isEmpty())return 0;
        var args=new ArrayList<Object>();args.add(workspace);args.addAll(ids);
        return jdbc.queryForObject("SELECT COUNT(*) FROM documents WHERE workspace_id=? AND folder_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",Long.class,args.toArray());
    }
    public List<FolderView> list(UUID workspace) {
        var values=jdbc.query("SELECT * FROM document_folders WHERE workspace_id=? ORDER BY LOWER(name),id",
            (rs,row)->new FolderView(rs.getObject("id",UUID.class),rs.getString("name"),Icon.valueOf(rs.getString("icon")),Color.valueOf(rs.getString("color")),0,rs.getTimestamp("created_at").toInstant(),rs.getObject("parent_id",UUID.class),workspace,rs.getObject("owner_id",UUID.class)),workspace);
        return values.stream().map(f->new FolderView(f.id(),f.name(),f.icon(),f.color(),documentCount(workspace,f.id()),f.createdAt(),f.parentId(),workspace,f.createdBy())).toList();
    }
    public boolean exists(UUID id,UUID workspace) { return jdbc.queryForObject("SELECT COUNT(*) FROM document_folders WHERE id=? AND workspace_id=?",Integer.class,id,workspace)>0; }
    public FolderView create(UUID owner,FolderRequest r) { access.ensurePersonal(owner);return create(access.require(owner,owner),r); }
    public FolderView create(Scope scope,FolderRequest r) {
        UUID id=UUID.randomUUID();Instant now=Instant.now();
        jdbc.update("INSERT INTO workspace_resources VALUES (?, ?, ?)",id,scope.workspaceId(),scope.organizationId());
        jdbc.update("INSERT INTO document_folders(id,owner_id,name,icon,color,created_at,workspace_id,organization_id,parent_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",id,scope.userId(),r.name(),r.icon().name(),r.color().name(),Timestamp.from(now),scope.workspaceId(),scope.organizationId(),r.parentId());
        return new FolderView(id,r.name(),r.icon(),r.color(),0,now,r.parentId(),scope.workspaceId(),scope.userId());
    }
    public boolean update(UUID id,UUID workspace,FolderRequest r) {
        return jdbc.update("UPDATE document_folders SET name=?,icon=?,color=?,parent_id=? WHERE id=? AND workspace_id=?",r.name(),r.icon().name(),r.color().name(),r.parentId(),id,workspace)==1;
    }
    public boolean move(UUID document,UUID workspace,UUID folder) { return jdbc.update("UPDATE documents SET folder_id=? WHERE id=? AND workspace_id=?",folder,document,workspace)==1; }
}
