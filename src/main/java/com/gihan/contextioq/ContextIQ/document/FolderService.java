package com.gihan.contextioq.ContextIQ.document;

import com.gihan.contextioq.ContextIQ.auth.service.CurrentUser;
import com.gihan.contextioq.ContextIQ.document.FolderDtos.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FolderService {
    private final FolderRepository folders;
    private final CurrentUser current;
    public FolderService(FolderRepository folders, CurrentUser current) { this.folders = folders; this.current = current; }
    public List<FolderView> list() { return folders.list(current.get().getId()); }

    @Transactional
    public FolderView save(UUID id, FolderRequest input) {
        UUID owner = current.get().getId();
        String name = input.name().strip();
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a folder name without control characters.");
        var request = new FolderRequest(name, input.icon(), input.color());
        try {
            if (id == null) return folders.create(owner, request);
            if (!folders.update(id, owner, request)) throw missing("Folder");
            return folders.list(owner).stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A folder with this name already exists.");
        }
    }

    @Transactional
    public void move(MoveRequest request) {
        UUID owner = current.get().getId();
        if (request.folderId() != null && !folders.exists(request.folderId(), owner)) throw missing("Folder");
        // All moves roll back if even one document is unavailable or belongs to another account.
        for (UUID document : new LinkedHashSet<>(request.documentIds()))
            if (!folders.move(document, owner, request.folderId())) throw missing("Document");
    }
    private ResponseStatusException missing(String kind) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, kind + " not found");
    }
}
