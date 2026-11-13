package com.gihan.contextioq.ContextIQ.rag;

import com.gihan.contextioq.ContextIQ.document.DocumentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {
    private final EmbeddingService embeddings;
    private final DocumentRepository documents;
    private final double threshold;
    private com.gihan.contextioq.ContextIQ.document.FolderRepository folders;
    private com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess access;

    @org.springframework.beans.factory.annotation.Autowired
    public RetrievalService(EmbeddingService embeddings, DocumentRepository documents,
                            @Value("${contextiq.rag.min-score:0.45}") double threshold,
                            com.gihan.contextioq.ContextIQ.document.FolderRepository folders,
                            com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess access) {
        this(embeddings, documents, threshold); this.folders=folders; this.access=access;
    }

    public RetrievalService(EmbeddingService embeddings, DocumentRepository documents,
                            @Value("${contextiq.rag.min-score:0.45}") double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("RAG_MIN_SCORE must be between 0 and 1");
        this.embeddings = embeddings;
        this.documents = documents;
        this.threshold = threshold;
    }

    public List<Source> retrieveScoped(com.gihan.contextioq.ContextIQ.workspace.WorkspaceAccess.Scope scope, String question, List<UUID> selected, List<UUID> selectedFolders) {
        access.require(scope.workspaceId(), scope.userId());
        var expanded = folders.descendants(scope.workspaceId(), selectedFolders);
        if (selected.isEmpty() && expanded.isEmpty()) return List.of();
        return documents.searchScoped(scope.workspaceId(), scope.userId(), embeddings.model(), embeddings.query(question), threshold, selected, expanded);
    }

    public List<Source> retrieve(UUID owner, String question, List<UUID> selected, List<UUID> folders) {
        if (selected.isEmpty() && folders.isEmpty()) return List.of();
        return documents.search(owner, embeddings.model(), embeddings.query(question), threshold, selected, folders);
    }

    public List<Source> retrieve(UUID owner, String question, List<UUID> selected) {
        if (selected.isEmpty()) return List.of();
        return documents.search(owner, embeddings.model(), embeddings.query(question), threshold, selected);
    }

    public List<Source> retrieve(UUID owner, String question) {
        if (documents.list(owner).isEmpty()) return List.of();
        return documents.search(owner, embeddings.model(), embeddings.query(question), threshold);
    }
}
