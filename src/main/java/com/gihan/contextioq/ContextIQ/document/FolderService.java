package com.gihan.contextioq.ContextIQ.document;
import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FolderService {
    private final FolderRepository folders; private final WorkspaceAccess access;
    public FolderService(FolderRepository folders,WorkspaceAccess access) { this.folders=folders;this.access=access; }
    public List<FolderView> list() { return folders.list(access.current().workspaceId()); }
    @Transactional public FolderView save(UUID id,FolderRequest input) {
        var scope=access.lock(access.current());scope.edit();UUID workspace=scope.workspaceId();
        String name=input.name().strip();
        if(name.isBlank()||name.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a folder name without control characters.");
        if(input.parentId()!=null) {
            if(!folders.exists(input.parentId(),workspace))throw WorkspaceAccess.missing();
            if(id!=null && folders.descendants(workspace,List.of(id)).contains(input.parentId()))throw new ResponseStatusException(HttpStatus.CONFLICT,"A folder cannot be placed inside itself or its descendants");
        }
        var request=new FolderRequest(name,input.icon(),input.color(),input.parentId());
        try {
            if(id==null)return folders.create(scope,request);
            if(!folders.update(id,workspace,request))throw WorkspaceAccess.missing();
            return folders.list(workspace).stream().filter(f->f.id().equals(id)).findFirst().orElseThrow();
        } catch(DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"A folder with this name already exists in this workspace"); }
    }
    @Transactional public void move(MoveRequest request) {
        var scope=access.lock(access.current());scope.edit();
        if(request.folderId()!=null&&!folders.exists(request.folderId(),scope.workspaceId()))throw WorkspaceAccess.missing();
        for(UUID doc:new LinkedHashSet<>(request.documentIds()))if(!folders.move(doc,scope.workspaceId(),request.folderId()))throw WorkspaceAccess.missing();
    }
}
